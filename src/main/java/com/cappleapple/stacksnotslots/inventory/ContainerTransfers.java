package com.cappleapple.stacksnotslots.inventory;

import com.cappleapple.stacksnotslots.data.ModAttachments;
import com.cappleapple.stacksnotslots.mixin.AbstractContainerMenuAccessor;
import com.cappleapple.stacksnotslots.network.BulkTransferPayload;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;

/** Server-authoritative bulk and browser transfers through native menu or item-handler semantics. */
public final class ContainerTransfers {
    private static final int MAX_WORLD_STACK_OPERATIONS = 65_536;
    private static final ThreadLocal<java.util.UUID> BULK_BACKEND_PLAYER = new ThreadLocal<>();

    private ContainerTransfers() {}

    public static boolean isBulkBackendRedirect(Player player) {
        return player != null && player.getUUID().equals(BULK_BACKEND_PLAYER.get());
    }

    public static List<TransferredStack> bulk(ServerPlayer player, BulkTransferPayload payload) {
        return payload.target() == BulkTransferPayload.Target.OPEN_MENU
                ? payload.direction() == BulkTransferPayload.Direction.TO_CONTAINER
                        ? movePlayerToOpenMenu(player) : moveOpenMenuToPlayer(player)
                : payload.direction() == BulkTransferPayload.Direction.TO_CONTAINER
                        ? movePlayerToLookedAt(player) : moveLookedAtToPlayer(player);
    }

    public static boolean moveBackendEntryToMenu(ServerPlayer player, ItemStack prototype) {
        DynamicCapacityInventory inventory = player.getData(ModAttachments.PLAYER_DATA).inventory();
        if (player.containerMenu == player.inventoryMenu) return inventory.moveBackendStackToMain(prototype);
        if (moveBackendEntryWithNativeQuickMove(player, prototype, inventory)) return true;
        List<Range> ranges = externalRanges(player.containerMenu, player);
        if (ranges.isEmpty()) return false;
        int available = inventory.extractAtOrAfter(prototype, prototype.getMaxStackSize(), 36, true).extractedAmount();
        if (available <= 0) return false;
        ItemStack moving = prototype.copyWithCount(available);
        int before = moving.getCount();
        moveThroughRanges(player.containerMenu, moving, ranges);
        int moved = before - moving.getCount();
        if (moved <= 0) return false;
        inventory.extractAtOrAfter(prototype, moved, 36, false);
        player.containerMenu.broadcastChanges();
        return true;
    }

    /**
     * Stages a backend stack in a real player slot, invokes the active menu's own quick-move path,
     * then restores the displaced player stack. This supports virtual storage terminals and custom
     * merge logic without compile-time dependencies or per-mod handlers.
     */
    private static boolean moveBackendEntryWithNativeQuickMove(ServerPlayer player, ItemStack prototype,
                                                                DynamicCapacityInventory inventory) {
        AbstractContainerMenu menu = player.containerMenu;
        for (int ordinal = 0; ordinal < 36; ordinal++) {
            int playerSlot = ordinal < 27 ? ordinal + 9 : ordinal - 27;
            for (int menuIndex = 0; menuIndex < menu.slots.size(); menuIndex++) {
                Slot slot = menu.slots.get(menuIndex);
                if (!isPlayerSlot(slot, player) || slot.getContainerSlot() != playerSlot || !slot.isActive()) continue;
                DynamicCapacityInventory.BackendQuickMoveStage stage =
                        inventory.beginBackendQuickMove(prototype, playerSlot);
                if (stage == null) return false;
                if (!slot.hasItem() || !slot.mayPickup(player)) {
                    inventory.finishBackendQuickMove(stage);
                    continue;
                }

                int moved;
                try {
                    menu.quickMoveStack(player, menuIndex);
                } finally {
                    moved = inventory.finishBackendQuickMove(stage);
                }
                if (moved <= 0) continue;
                menu.broadcastChanges();
                return true;
            }
        }
        return false;
    }

    private static List<TransferredStack> movePlayerToOpenMenu(ServerPlayer player) {
        if (player.containerMenu == player.inventoryMenu) return List.of();
        List<Range> ranges = externalRanges(player.containerMenu, player);
        if (ranges.isEmpty()) return List.of();
        DynamicCapacityInventory inventory = player.getData(ModAttachments.PLAYER_DATA).inventory();
        TransferAccumulator moved = new TransferAccumulator();
        for (ItemStack owned : inventory.backingStacks()) {
            if (owned.isEmpty()) continue;
            ItemStack moving = owned.copy();
            int before = moving.getCount();
            moveThroughRanges(player.containerMenu, moving, ranges);
            int accepted = before - moving.getCount();
            if (accepted <= 0) continue;
            inventory.extract(owned, accepted, false);
            moved.add(owned, accepted);
        }
        player.containerMenu.broadcastChanges();
        return moved.values();
    }

    private static List<TransferredStack> moveOpenMenuToPlayer(ServerPlayer player) {
        if (player.containerMenu == player.inventoryMenu) return List.of();
        TransferAccumulator moved = new TransferAccumulator();
        AbstractContainerMenu menu = player.containerMenu;
        BULK_BACKEND_PLAYER.set(player.getUUID());
        try {
            for (int index = 0; index < menu.slots.size(); index++) {
                Slot slot = menu.slots.get(index);
                if (isPlayerSlot(slot, player) || !slot.hasItem() || !slot.mayPickup(player)) continue;
                ItemStack before = slot.getItem().copy();
                menu.quickMoveStack(player, index);
                ItemStack after = slot.getItem();
                int amount = ItemStack.isSameItemSameComponents(before, after) ? before.getCount() - after.getCount() : before.getCount();
                if (amount > 0) moved.add(before, amount);
            }
        } finally {
            BULK_BACKEND_PLAYER.remove();
        }
        menu.broadcastChanges();
        return moved.values();
    }

    private static List<TransferredStack> movePlayerToLookedAt(ServerPlayer player) {
        IItemHandler handler = lookedAtHandler(player);
        if (handler == null) return List.of();
        DynamicCapacityInventory inventory = player.getData(ModAttachments.PLAYER_DATA).inventory();
        TransferAccumulator moved = new TransferAccumulator();
        for (ItemStack owned : inventory.backingStacks()) {
            if (owned.isEmpty()) continue;
            ItemStack remainder = ItemHandlerHelper.insertItemStacked(handler, owned.copy(), false);
            int accepted = owned.getCount() - remainder.getCount();
            if (accepted <= 0) continue;
            inventory.extract(owned, accepted, false);
            moved.add(owned, accepted);
        }
        return moved.values();
    }

    private static List<TransferredStack> moveLookedAtToPlayer(ServerPlayer player) {
        IItemHandler handler = lookedAtHandler(player);
        if (handler == null) return List.of();
        TransferAccumulator moved = new TransferAccumulator();
        int operations = 0;
        for (int slot = 0; slot < handler.getSlots() && operations < MAX_WORLD_STACK_OPERATIONS; slot++) {
            while (operations++ < MAX_WORLD_STACK_OPERATIONS) {
                ItemStack available = handler.extractItem(slot, Integer.MAX_VALUE, true);
                if (available.isEmpty()) break;
                var proposal = InventoryTransactions.insertIntoBackend(player, available, true);
                int accepted = proposal.acceptedAmount();
                if (accepted <= 0) return moved.values();
                ItemStack extracted = handler.extractItem(slot, accepted, false);
                if (extracted.isEmpty()) break;
                var insertion = InventoryTransactions.insertIntoBackend(player, extracted, false);
                if (insertion.acceptedAmount() > 0) moved.add(extracted, insertion.acceptedAmount());
                if (insertion.acceptedAmount() < extracted.getCount()) {
                    ItemStack remainder = extracted.copyWithCount(extracted.getCount() - insertion.acceptedAmount());
                    ItemHandlerHelper.insertItemStacked(handler, remainder, false);
                    return moved.values();
                }
            }
        }
        return moved.values();
    }

    private static IItemHandler lookedAtHandler(ServerPlayer player) {
        HitResult hit = player.pick(player.blockInteractionRange(), 0.0F, false);
        if (!(hit instanceof BlockHitResult blockHit) || hit.getType() != HitResult.Type.BLOCK) return null;
        if (!player.mayInteract(player.level(), blockHit.getBlockPos())) return null;
        return player.level().getCapability(Capabilities.ItemHandler.BLOCK, blockHit.getBlockPos(), blockHit.getDirection());
    }

    private static void moveThroughRanges(AbstractContainerMenu menu, ItemStack moving, List<Range> ranges) {
        AbstractContainerMenuAccessor accessor = (AbstractContainerMenuAccessor)menu;
        for (Range range : ranges) {
            if (moving.isEmpty()) break;
            accessor.sns$moveItemStackTo(moving, range.start(), range.end(), false);
        }
    }

    private static List<Range> externalRanges(AbstractContainerMenu menu, ServerPlayer player) {
        ArrayList<Range> result = new ArrayList<>();
        int start = -1;
        for (int index = 0; index <= menu.slots.size(); index++) {
            boolean external = index < menu.slots.size() && !isPlayerSlot(menu.slots.get(index), player);
            if (external && start < 0) start = index;
            else if (!external && start >= 0) {
                result.add(new Range(start, index));
                start = -1;
            }
        }
        return result;
    }

    private static boolean isPlayerSlot(Slot slot, ServerPlayer player) {
        return slot.container == player.getInventory();
    }

    public record TransferredStack(ItemStack prototype, long quantity) {
        public TransferredStack { prototype = prototype.copyWithCount(1); }
        @Override public ItemStack prototype() { return prototype.copy(); }
    }

    private record Range(int start, int end) {}

    private static final class TransferAccumulator {
        private final ArrayList<TransferredStack> values = new ArrayList<>();
        private void add(ItemStack stack, long amount) {
            for (int index = 0; index < values.size(); index++) {
                TransferredStack current = values.get(index);
                if (!ItemStack.isSameItemSameComponents(current.prototype, stack)) continue;
                values.set(index, new TransferredStack(stack, current.quantity + amount));
                return;
            }
            values.add(new TransferredStack(stack, amount));
        }
        private List<TransferredStack> values() { return List.copyOf(values); }
    }
}
