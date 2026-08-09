package com.cappleapple.stacksnotslots.mixin;

import com.cappleapple.stacksnotslots.data.ModAttachments;
import com.cappleapple.stacksnotslots.inventory.InventoryTransactions;
import com.cappleapple.stacksnotslots.inventory.ContainerTransfers;
import com.cappleapple.stacksnotslots.network.ModNetwork;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Routes shift-click insertion into player storage through the unbounded logical append path. */
@Mixin(AbstractContainerMenu.class)
public abstract class AbstractContainerMenuMixin {
    @Shadow @Final public NonNullList<Slot> slots;
    @Unique private UUID sns$quickMovePlayerId;
    @Unique private List<ItemStack> sns$quickMoveVisibleBefore = List.of();
    @Unique private boolean sns$quickMoveBackendOnly;

    @Inject(method = "moveItemStackTo", at = @At("HEAD"), cancellable = true)
    private void sns$insertIntoUnifiedPlayerInventory(
            ItemStack source, int startIndex, int endIndex, boolean reverseDirection,
            CallbackInfoReturnable<Boolean> callback
    ) {
        Inventory playerInventory = sns$storageTarget(startIndex, endIndex);
        if (playerInventory == null
                || !playerInventory.player.getData(ModAttachments.PLAYER_DATA).migratedVanillaInventory()
                || source.isEmpty()) return;

        var inventory = playerInventory.player.getData(ModAttachments.PLAYER_DATA).inventory();
        if (inventory.ownsReference(source)) {
            // A player-owned source is an ordinary main-grid/hotbar/equipment move. Let vanilla
            // honor the exact destination range instead of treating it as external insertion.
            return;
        }
        boolean backendOnly = ModNetwork.isBrowserOpen(playerInventory.player)
                || ContainerTransfers.isBulkBackendRedirect(playerInventory.player);
        var insertion = backendOnly
                ? InventoryTransactions.insertIntoBackend(playerInventory.player, source, false)
                : InventoryTransactions.insertInPlayerTransferOrder(playerInventory.player, source, false);
        if (!insertion.acceptedAnything()) {
            callback.setReturnValue(false);
            return;
        }
        source.shrink(insertion.acceptedAmount());
        callback.setReturnValue(true);
    }

    /** Captures custom-menu quick moves which bypass vanilla's moveItemStackTo helper. */
    @Inject(method = "clicked", at = @At("HEAD"))
    private void sns$captureExternalQuickMove(int slotId, int button, ClickType clickType, Player player,
                                              CallbackInfo callback) {
        sns$quickMovePlayerId = null;
        sns$quickMoveVisibleBefore = List.of();
        if (!(player instanceof ServerPlayer) || clickType != ClickType.QUICK_MOVE
                || slotId < 0 || slotId >= slots.size()
                || !player.getData(ModAttachments.PLAYER_DATA).migratedVanillaInventory()) return;
        Slot source = slots.get(slotId);
        if (source.container == player.getInventory() || !source.hasItem()) return;
        sns$quickMovePlayerId = player.getUUID();
        sns$quickMoveVisibleBefore = player.getData(ModAttachments.PLAYER_DATA)
                .inventory().visibleCompatibilitySnapshot();
        sns$quickMoveBackendOnly = ModNetwork.isBrowserOpen(player)
                || ContainerTransfers.isBulkBackendRedirect(player);
    }

    /** Repositions only what the custom menu added, preserving every pre-existing visible stack. */
    @Inject(method = "clicked", at = @At("RETURN"))
    private void sns$finishExternalQuickMove(int slotId, int button, ClickType clickType, Player player,
                                             CallbackInfo callback) {
        UUID capturedPlayerId = sns$quickMovePlayerId;
        List<ItemStack> visibleBefore = sns$quickMoveVisibleBefore;
        boolean backendOnly = sns$quickMoveBackendOnly;
        sns$quickMovePlayerId = null;
        sns$quickMoveVisibleBefore = List.of();
        if (capturedPlayerId == null || !capturedPlayerId.equals(player.getUUID())) return;
        var inventory = player.getData(ModAttachments.PLAYER_DATA).inventory();
        if (!inventory.relocateReceivedVisibleStacks(visibleBefore, backendOnly)) return;
        player.getInventory().setChanged();
        ((AbstractContainerMenu)(Object)this).broadcastChanges();
    }

    @Unique
    private Inventory sns$storageTarget(int startIndex, int endIndex) {
        if (startIndex < 0 || endIndex > slots.size() || startIndex >= endIndex) return null;
        Inventory target = null;
        for (int index = startIndex; index < endIndex; index++) {
            Slot slot = slots.get(index);
            if (!(slot.container instanceof Inventory inventory)
                    || slot.getContainerSlot() < 0
                    || slot.getContainerSlot() >= Inventory.INVENTORY_SIZE) return null;
            if (target != null && target != inventory) return null;
            target = inventory;
        }
        return target;
    }

}
