package com.cappleapple.stacksnotslots.api.inventory;

import java.util.Objects;

/** Creates standalone inventories without player, screen, or Bundled Not Siloed assumptions. */
public final class InventoryFactory {
    private InventoryFactory() { }

    public static MutableCapacityInventory create(long capacity) {
        return create(InventoryOptions.fixed(capacity));
    }

    public static MutableCapacityInventory create(InventoryOptions options) {
        Objects.requireNonNull(options, "options");
        return new DynamicCapacityInventory(options.capacity(), options.mutationListener(),
                options.insertionRule(), options.extractionRule());
    }
}
