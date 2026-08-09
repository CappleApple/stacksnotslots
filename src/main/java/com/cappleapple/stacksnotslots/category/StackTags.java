package com.cappleapple.stacksnotslots.category;

import java.util.stream.Stream;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;

/** Item tags plus the block tags represented by a {@link BlockItem}. */
public final class StackTags {
    private StackTags() {}

    public static boolean is(ItemStack stack, ResourceLocation tag) {
        if (stack.is(TagKey.create(Registries.ITEM, tag))) return true;
        return stack.getItem() instanceof BlockItem blockItem
                && blockItem.getBlock().defaultBlockState().is(TagKey.create(Registries.BLOCK, tag));
    }

    public static Stream<ResourceLocation> locations(ItemStack stack) {
        Stream<ResourceLocation> itemTags = stack.getTags().map(TagKey::location);
        if (!(stack.getItem() instanceof BlockItem blockItem)) return itemTags;
        Stream<ResourceLocation> blockTags = blockItem.getBlock().defaultBlockState().getTags().map(TagKey::location);
        return Stream.concat(itemTags, blockTags).distinct();
    }
}
