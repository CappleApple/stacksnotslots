package com.cappleapple.stacksnotslots.api;

import java.util.List;
import net.minecraft.world.item.ItemStack;

/**
 * Stable logical-inventory contract. Implementations are capacity-based and are not required to
 * expose or emulate a fixed number of vanilla slots.
 */
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

    default boolean canInsert(ItemStack stack) {
        return stack != null && !stack.isEmpty() && insert(stack, true).acceptedAmount() > 0;
    }

    default boolean canExtract(ItemStack prototype) {
        return prototype != null && !prototype.isEmpty() && extract(prototype, 1, true).extractedAmount() > 0;
    }

    default long count(ItemStack prototype) {
        if (prototype == null || prototype.isEmpty()) return 0;
        return entries().stream()
                .filter(entry -> ItemStack.isSameItemSameComponents(entry.representative(), prototype))
                .mapToLong(LogicalInventoryEntry::quantity)
                .sum();
    }

    default long totalItemCount() {
        return entries().stream().mapToLong(LogicalInventoryEntry::quantity).sum();
    }

    long revision();
    AutoCloseable addListener(InventoryChangeListener listener);
}
