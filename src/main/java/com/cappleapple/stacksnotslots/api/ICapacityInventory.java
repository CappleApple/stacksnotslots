package com.cappleapple.stacksnotslots.api;

import java.util.List;
import net.minecraft.world.item.ItemStack;

public interface ICapacityInventory {
    long capacity();
    long usedCapacity();
    default CapacityAmount exactCapacity() { return CapacityAmount.of(capacity()); }
    default CapacityAmount exactUsedCapacity() { return CapacityAmount.of(usedCapacity()); }
    default CapacityAmount exactRemainingCapacity() {
        return exactCapacity().subtract(exactUsedCapacity()).maxZero();
    }
    /** Conservative whole-unit compatibility view of the exact remaining capacity. */
    default long remainingCapacity() { return exactRemainingCapacity().floorToLong(); }
    default boolean isOverCapacity() { return exactUsedCapacity().compareTo(exactCapacity()) > 0; }
    List<LogicalInventoryEntry> entries();
    InsertionResult insert(ItemStack stack, boolean simulate);
    ExtractionResult extract(ItemStack prototype, int amount, boolean simulate);
    long revision();
    AutoCloseable addListener(InventoryChangeListener listener);
}
