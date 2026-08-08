package com.cappleapple.stacksnotslots.api;

@FunctionalInterface
public interface InventoryChangeListener {
    void inventoryChanged(ICapacityInventory inventory, long revision);
}
