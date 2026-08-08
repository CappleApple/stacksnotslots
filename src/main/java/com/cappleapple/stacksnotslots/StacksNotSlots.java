package com.cappleapple.stacksnotslots;

import com.mojang.logging.LogUtils;
import com.cappleapple.stacksnotslots.attribute.ModAttributes;
import com.cappleapple.stacksnotslots.compat.PlayerItemHandlerProvider;
import com.cappleapple.stacksnotslots.config.ClientConfig;
import com.cappleapple.stacksnotslots.config.CommonConfig;
import com.cappleapple.stacksnotslots.data.ModAttachments;
import com.cappleapple.stacksnotslots.network.ModNetwork;
import com.cappleapple.stacksnotslots.server.ServerEvents;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

@Mod(StacksNotSlots.MOD_ID)
public final class StacksNotSlots {
    public static final String MOD_ID = "stacksnotslots";
    public static final Logger LOGGER = LogUtils.getLogger();

    public StacksNotSlots(IEventBus modBus, ModContainer container) {
        ModAttributes.ATTRIBUTES.register(modBus);
        ModAttachments.ATTACHMENTS.register(modBus);
        modBus.addListener(ModAttributes::addPlayerAttributes);
        modBus.addListener(PlayerItemHandlerProvider::registerCapabilities);
        modBus.addListener(ModNetwork::registerPayloads);

        container.registerConfig(ModConfig.Type.COMMON, CommonConfig.SPEC, "stacksnotslots-common.toml");
        container.registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC, "stacksnotslots-client.toml");
        NeoForge.EVENT_BUS.register(ServerEvents.class);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
