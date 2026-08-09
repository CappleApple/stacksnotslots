package com.cappleapple.stacksnotslots.api;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/** Immutable public description of a category rule. Regex rules use expression instead of target. */
public record CategoryRuleView(Type type, @Nullable ResourceLocation target, @Nullable String expression) {
    public CategoryRuleView(Type type, ResourceLocation target) {
        this(type, target, null);
    }

    public enum Type { ITEM, TAG, MOD_ID, REGEX }
}
