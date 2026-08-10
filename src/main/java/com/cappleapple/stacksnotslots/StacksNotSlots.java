package com.cappleapple.stacksnotslots;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

/** NeoForge entry point for the independent inventory/storage library. */
@Mod(StacksNotSlots.MOD_ID)
public final class StacksNotSlots {
    public static final String MOD_ID = "stacksnotslots";
    public static final Logger LOGGER = LogUtils.getLogger();

    public StacksNotSlots(IEventBus ignored) {
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
