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
    private static final String TAG_ICON_PREFIX = "tag/";
    private static final Map<CategoryDefinition, List<ItemStack>> CATEGORY_ITEMS = new HashMap<>();
    private static final Map<CategoryRule, List<ItemStack>> RULE_ITEMS = new HashMap<>();

    private CategoryIcons() {}

    public static ItemStack displayStack(CategoryDefinition category) {
        if (category == null) return new ItemStack(Items.CHEST);
        CategoryRule iconTag = decodeTagIcon(category.icon());
        if (iconTag != null) return displayStack(iconTag);
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

    public static ResourceLocation parseIcon(String input) {
        String value = input.trim();
        if (value.isEmpty()) return DYNAMIC_ICON;
        if (!value.startsWith("#")) return ResourceLocation.tryParse(value);
        ResourceLocation tag = ResourceLocation.tryParse(value.substring(1));
        return tag == null ? null : StacksNotSlots.id(TAG_ICON_PREFIX + tag.getNamespace() + "/" + tag.getPath());
    }

    public static String formatIcon(ResourceLocation icon) {
        CategoryRule tag = decodeTagIcon(icon);
        if (tag != null) return "#" + tag.target();
        return DYNAMIC_ICON.equals(icon) ? "" : icon.toString();
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
        ArrayList<ItemStack> result = new ArrayList<>();
        if (rule.type() == CategoryRule.Type.ITEM) {
            return BuiltInRegistries.ITEM.getOptional(rule.target())
                    .map(Item::getDefaultInstance).filter(stack -> !stack.isEmpty()).map(List::of).orElse(List.of());
        }
        TagKey<Item> tag = rule.type() == CategoryRule.Type.TAG ? TagKey.create(Registries.ITEM, rule.target()) : null;
        for (Item item : BuiltInRegistries.ITEM) {
            ItemStack stack = item.getDefaultInstance();
            if (stack.isEmpty()) continue;
            if (rule.type() == CategoryRule.Type.TAG && stack.is(tag)
                    || rule.type() == CategoryRule.Type.MOD_ID
                    && BuiltInRegistries.ITEM.getKey(item).getNamespace().equals(rule.target().getNamespace())) {
                result.add(stack);
            }
        }
        return List.copyOf(result);
    }

    private static CategoryRule decodeTagIcon(ResourceLocation icon) {
        if (!StacksNotSlots.MOD_ID.equals(icon.getNamespace()) || !icon.getPath().startsWith(TAG_ICON_PREFIX)) return null;
        String encoded = icon.getPath().substring(TAG_ICON_PREFIX.length());
        int separator = encoded.indexOf('/');
        if (separator <= 0 || separator + 1 >= encoded.length()) return null;
        ResourceLocation target = ResourceLocation.tryBuild(encoded.substring(0, separator), encoded.substring(separator + 1));
        return target == null ? null : new CategoryRule(CategoryRule.Type.TAG, target);
    }

    private static ItemStack cycle(List<ItemStack> stacks) {
        if (stacks.isEmpty()) return new ItemStack(Items.BARRIER);
        int index = (int)Math.floorMod(Util.getMillis() / 1200L, stacks.size());
        return stacks.get(index);
    }
}
