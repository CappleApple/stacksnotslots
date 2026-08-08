package com.cappleapple.stacksnotslots.category;

import net.minecraft.resources.ResourceLocation;

public record CategoryRule(Type type, ResourceLocation target) {
    public enum Type { ITEM, TAG }
}
