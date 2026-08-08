package com.cappleapple.stacksnotslots.api;

import net.minecraft.resources.ResourceLocation;

/** Immutable public description of one exact-item or item-tag category rule. */
public record CategoryRuleView(Type type, ResourceLocation target) {
    public enum Type { ITEM, TAG }
}
