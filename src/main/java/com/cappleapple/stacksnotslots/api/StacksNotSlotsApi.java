package com.cappleapple.stacksnotslots.api;

import com.cappleapple.stacksnotslots.api.inventory.InventoryFactory;
import com.cappleapple.stacksnotslots.api.inventory.InventoryOptions;
import com.cappleapple.stacksnotslots.api.inventory.MutableCapacityInventory;
import com.cappleapple.stacksnotslots.internal.inventory.CapacityCosts;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** Stable public entry point for standalone capacity inventories and capacity accounting. */
public final class StacksNotSlotsApi {
    private StacksNotSlotsApi() { }

    public static MutableCapacityInventory createInventory(long capacity) {
        return InventoryFactory.create(capacity);
    }

    public static MutableCapacityInventory createInventory(InventoryOptions options) {
        return InventoryFactory.create(options);
    }

    public static long capacityCost(ItemStack stack) {
        return CapacityCosts.cost(stack, stack == null ? 0 : stack.getCount());
    }

    /** Exact rational capacity cost, including items whose maximum stack size does not divide 64. */
    public static CapacityAmount exactCapacityCost(ItemStack stack) {
        return CapacityCosts.costExact(stack, stack == null ? 0 : stack.getCount());
    }

    public static AutoCloseable registerCapacityCostProvider(ResourceLocation id, int priority,
                                                              CapacityCostProvider provider) {
        return CapacityCosts.register(id, priority, provider);
    }
}
