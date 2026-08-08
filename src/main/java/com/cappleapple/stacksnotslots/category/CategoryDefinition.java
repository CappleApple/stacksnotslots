package com.cappleapple.stacksnotslots.category;

import java.util.List;
import net.minecraft.resources.ResourceLocation;

public record CategoryDefinition(
        ResourceLocation id,
        String displayName,
        ResourceLocation icon,
        int order,
        List<CategoryRule> includes,
        List<CategoryRule> excludes,
        long pickupLimit,
        SortMode sortMode,
        boolean enabled,
        boolean allItems
) {
    public CategoryDefinition {
        includes = List.copyOf(includes);
        excludes = List.copyOf(excludes);
        if (pickupLimit < -1) throw new IllegalArgumentException("pickupLimit must be -1 or non-negative");
    }
}
