package com.cappleapple.stacksnotslots.api;

import com.cappleapple.stacksnotslots.internal.inventory.CapacityCosts;
import net.minecraft.world.item.ItemStack;

/** Public capacity-accounting helpers used by inventories and their consumers. */
public final class CapacityUnits {
    public static final long STACK_EQUIVALENT = CapacityCosts.STACK_EQUIVALENT_UNITS;

    private CapacityUnits() { }

    public static long unitCost(ItemStack stack) { return CapacityCosts.unitCost(stack); }
    public static CapacityAmount exactUnitCost(ItemStack stack) { return CapacityCosts.unitCostExact(stack); }
    public static long cost(ItemStack stack, long quantity) { return CapacityCosts.cost(stack, quantity); }
    public static CapacityAmount exactCost(ItemStack stack, long quantity) { return CapacityCosts.costExact(stack, quantity); }
}
