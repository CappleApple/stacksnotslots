package com.cappleapple.stacksnotslots.api;

import java.util.List;
import net.minecraft.world.item.ItemStack;

public interface ICapacityInventory {
    long capacity();
    long usedCapacity();
    default long remainingCapacity() { return Math.max(0, capacity() - usedCapacity()); }
    default boolean isOverCapacity() { return usedCapacity() > capacity(); }
    List<LogicalInventoryEntry> entries();
    InsertionResult insert(ItemStack stack, boolean simulate);
    ExtractionResult extract(ItemStack prototype, int amount, boolean simulate);
    long revision();
    AutoCloseable addListener(InventoryChangeListener listener);
}
