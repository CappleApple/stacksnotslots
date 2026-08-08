package com.cappleapple.stacksnotslots.api;

import net.minecraft.world.item.ItemStack;

public record InsertionResult(
        int requestedAmount,
        int acceptedAmount,
        ItemStack remainder,
        long capacityConsumed,
        InsertionRejection rejection
) {
    public InsertionResult {
        remainder = remainder.copy();
    }

    @Override
    public ItemStack remainder() {
        return remainder.copy();
    }

    public boolean acceptedAnything() {
        return acceptedAmount > 0;
    }

    public boolean acceptedAll() {
        return acceptedAmount == requestedAmount;
    }
}
