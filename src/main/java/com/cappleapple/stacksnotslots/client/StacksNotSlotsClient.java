package com.cappleapple.stacksnotslots.client;

import com.cappleapple.stacksnotslots.StacksNotSlots;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value = StacksNotSlots.MOD_ID, dist = Dist.CLIENT)
public final class StacksNotSlotsClient {
    public StacksNotSlotsClient(IEventBus modBus, ModContainer container) {
        modBus.addListener(ClientKeyMappings::register);
        NeoForge.EVENT_BUS.register(ClientEvents.class);
    }
}
