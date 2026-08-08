package com.cappleapple.stacksnotslots.compat;

import com.cappleapple.stacksnotslots.api.InsertionResult;
import com.cappleapple.stacksnotslots.inventory.DynamicCapacityInventory;
import com.cappleapple.stacksnotslots.inventory.InsertionContext;
import com.cappleapple.stacksnotslots.inventory.InventoryTransactions;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandlerModifiable;

/** Dynamic compatibility view: one index per legal backing stack plus an always-growing append slot. */
public final class DynamicItemHandler implements IItemHandlerModifiable {
    private final DynamicCapacityInventory inventory;
    private final Player player;

    public DynamicItemHandler(Player player) {
        this.player = player;
        this.inventory = player.getData(com.cappleapple.stacksnotslots.data.ModAttachments.PLAYER_DATA).inventory();
    }

    /** Core-only constructor used by isolated adapter tests. */
    public DynamicItemHandler(DynamicCapacityInventory inventory) {
        this.player = null;
        this.inventory = inventory;
    }

    @Override public int getSlots() { return inventory.compatibilitySlotCount(); }
    @Override public ItemStack getStackInSlot(int slot) { return inventory.syntheticStack(slot); }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        checkSlot(slot);
        if (stack.isEmpty()) return ItemStack.EMPTY;
        ItemStack existing = inventory.syntheticStack(slot);
        if (!existing.isEmpty() && !ItemStack.isSameItemSameComponents(existing, stack)) return stack;
        InsertionResult result = player == null
                ? inventory.insert(stack, simulate)
                : InventoryTransactions.insert(player, stack, InsertionContext.MANUAL_TRANSFER, simulate);
        return result.remainder();
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        checkSlot(slot);
        return inventory.extractSyntheticSlot(slot, amount, simulate);
    }

    @Override public int getSlotLimit(int slot) {
        checkSlot(slot);
        ItemStack stack = inventory.syntheticStack(slot);
        return stack.isEmpty() ? 64 : stack.getMaxStackSize();
    }

    @Override public boolean isItemValid(int slot, ItemStack stack) { checkSlot(slot); return !stack.isEmpty(); }
    @Override public void setStackInSlot(int slot, ItemStack stack) { checkSlot(slot); inventory.replaceSyntheticSlot(slot, stack); }

    private void checkSlot(int slot) {
        if (slot < 0 || slot >= getSlots()) throw new RuntimeException("Slot " + slot + " not in valid range [0," + getSlots() + ")");
    }
}
