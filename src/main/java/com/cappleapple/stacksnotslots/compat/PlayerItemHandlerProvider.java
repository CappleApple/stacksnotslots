package com.cappleapple.stacksnotslots.compat;

import net.minecraft.world.entity.EntityType;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

public final class PlayerItemHandlerProvider {
    private PlayerItemHandlerProvider() {}

    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerEntity(Capabilities.ItemHandler.ENTITY, EntityType.PLAYER,
                (player, ignored) -> new DynamicItemHandler(player));
        event.registerEntity(Capabilities.ItemHandler.ENTITY_AUTOMATION, EntityType.PLAYER,
                (player, ignored) -> new DynamicItemHandler(player));
    }
}
