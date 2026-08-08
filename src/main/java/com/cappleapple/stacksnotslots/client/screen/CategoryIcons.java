package com.cappleapple.stacksnotslots.client.screen;

import com.cappleapple.stacksnotslots.StacksNotSlots;
import com.cappleapple.stacksnotslots.category.CategoryDefinition;
import com.cappleapple.stacksnotslots.category.CategoryMatcher;
import com.cappleapple.stacksnotslots.category.CategoryRule;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.Util;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Resolves fixed icons and time-cycled dynamic category/tag previews. */
public final class CategoryIcons {
    public static final ResourceLocation DYNAMIC_ICON = StacksNotSlots.id("dynamic_icon");
    private static final Map<CategoryDefinition, List<ItemStack>> CATEGORY_ITEMS = new HashMap<>();
    private static final Map<CategoryRule, List<ItemStack>> RULE_ITEMS = new HashMap<>();

    private CategoryIcons() {}

    public static ItemStack displayStack(CategoryDefinition category) {
        if (category == null) return new ItemStack(Items.CHEST);
        if (!DYNAMIC_ICON.equals(category.icon())) {
            ItemStack fixed = BuiltInRegistries.ITEM.getOptional(category.icon())
                    .map(Item::getDefaultInstance).orElse(ItemStack.EMPTY);
            if (!fixed.isEmpty()) return fixed;
        }
        return cycle(CATEGORY_ITEMS.computeIfAbsent(category, CategoryIcons::matchingCategoryItems));
    }

    public static ItemStack displayStack(CategoryRule rule) {
        return cycle(RULE_ITEMS.computeIfAbsent(rule, CategoryIcons::matchingRuleItems));
    }

    private static List<ItemStack> matchingCategoryItems(CategoryDefinition category) {
        ArrayList<ItemStack> result = new ArrayList<>();
        for (Item item : BuiltInRegistries.ITEM) {
            ItemStack stack = item.getDefaultInstance();
            if (!stack.isEmpty() && CategoryMatcher.matches(category, stack)) result.add(stack);
        }
        return List.copyOf(result);
    }

    private static List<ItemStack> matchingRuleItems(CategoryRule rule) {
        if (rule.type() == CategoryRule.Type.ITEM) {
            return BuiltInRegistries.ITEM.getOptional(rule.target())
                    .map(Item::getDefaultInstance).filter(stack -> !stack.isEmpty()).map(List::of).orElse(List.of());
        }
        TagKey<Item> tag = TagKey.create(Registries.ITEM, rule.target());
        ArrayList<ItemStack> result = new ArrayList<>();
        for (Item item : BuiltInRegistries.ITEM) {
            ItemStack stack = item.getDefaultInstance();
            if (!stack.isEmpty() && stack.is(tag)) result.add(stack);
        }
        return List.copyOf(result);
    }

    private static ItemStack cycle(List<ItemStack> stacks) {
        if (stacks.isEmpty()) return new ItemStack(Items.BARRIER);
        int index = (int)Math.floorMod(Util.getMillis() / 1200L, stacks.size());
        return stacks.get(index);
    }
}
