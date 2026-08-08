package com.cappleapple.stacksnotslots.api;

import net.minecraft.world.item.ItemStack;

/** Supplies a positive integer capacity cost per individual item, or {@code -1} when not applicable. */
@FunctionalInterface
public interface CapacityCostProvider {
    long capacityCostPerItem(ItemStack stack);
}
