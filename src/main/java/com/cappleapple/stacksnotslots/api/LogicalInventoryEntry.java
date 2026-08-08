package com.cappleapple.stacksnotslots.api;

import net.minecraft.world.item.ItemStack;

/** An immutable aggregate view. The representative count is one; quantity is the total owned amount. */
public record LogicalInventoryEntry(ItemStack representative, long quantity, int backingStackCount) {
    public LogicalInventoryEntry {
        representative = representative.copyWithCount(1);
    }

    @Override
    public ItemStack representative() {
        return representative.copy();
    }
}
