package com.cappleapple.stacksnotslots.category;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;

public final class CategoryMatcher {
    private CategoryMatcher() {}

    public static boolean matches(CategoryDefinition category, ItemStack stack) {
        if (!category.enabled() || stack.isEmpty()) return false;
        boolean included = category.allItems() || category.includes().stream().anyMatch(rule -> matches(rule, stack));
        return included && category.excludes().stream().noneMatch(rule -> matches(rule, stack));
    }

    public static boolean matches(CategoryRule rule, ItemStack stack) {
        if (stack.isEmpty()) return false;
        return switch (rule.type()) {
            case ITEM -> BuiltInRegistries.ITEM.getOptional(rule.target()).map(stack::is).orElse(false);
            case TAG -> StackTags.is(stack, rule.target());
            case MOD_ID -> BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace().equals(rule.target().getNamespace());
            case REGEX -> matchesRegex(rule, stack);
        };
    }

    private static boolean matchesRegex(CategoryRule rule, ItemStack stack) {
        var itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (rule.finds(stack.getHoverName().getString()) || rule.finds(itemId.toString())
                || rule.finds(itemId.getNamespace()) || StackTags.locations(stack).anyMatch(tag -> rule.finds(tag.toString()))) {
            return true;
        }
        return stack.getItem() instanceof BlockItem blockItem
                && rule.finds("block:" + BuiltInRegistries.BLOCK.getKey(blockItem.getBlock()));
    }
}
