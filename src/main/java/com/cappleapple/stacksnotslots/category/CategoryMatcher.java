package com.cappleapple.stacksnotslots.category;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;

public final class CategoryMatcher {
    private CategoryMatcher() {}

    public static boolean matches(CategoryDefinition category, ItemStack stack) {
        if (!category.enabled() || stack.isEmpty()) return false;
        boolean included = category.allItems() || category.includes().stream().anyMatch(rule -> matches(rule, stack));
        return included && category.excludes().stream().noneMatch(rule -> matches(rule, stack));
    }

    private static boolean matches(CategoryRule rule, ItemStack stack) {
        return switch (rule.type()) {
            case ITEM -> BuiltInRegistries.ITEM.getOptional(rule.target()).map(stack::is).orElse(false);
            case TAG -> stack.is(TagKey.create(Registries.ITEM, rule.target()));
        };
    }
}
