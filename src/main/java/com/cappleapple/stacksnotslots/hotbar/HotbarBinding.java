package com.cappleapple.stacksnotslots.hotbar;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

public record HotbarBinding(BindingType type, @Nullable ResourceLocation target, @Nullable StackReference selectedEntry) {
    public static HotbarBinding empty() { return new HotbarBinding(BindingType.EMPTY, null, null); }

    public HotbarBinding withSelected(ItemStack stack) {
        return new HotbarBinding(type, target, stack.isEmpty() ? null : StackReference.of(stack));
    }
}
