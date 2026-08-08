package com.cappleapple.stacksnotslots.api;

import java.util.List;
import net.minecraft.world.item.ItemStack;

public record ExtractionResult(int requestedAmount, int extractedAmount, List<ItemStack> extractedStacks) {
    public ExtractionResult {
        extractedStacks = extractedStacks.stream().map(ItemStack::copy).toList();
    }

    @Override
    public List<ItemStack> extractedStacks() {
        return extractedStacks.stream().map(ItemStack::copy).toList();
    }

    public boolean extractedAll() {
        return requestedAmount == extractedAmount;
    }
}
