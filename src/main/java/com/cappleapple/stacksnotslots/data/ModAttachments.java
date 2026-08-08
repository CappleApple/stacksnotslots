package com.cappleapple.stacksnotslots.data;

import com.cappleapple.stacksnotslots.StacksNotSlots;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public final class ModAttachments {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, StacksNotSlots.MOD_ID);
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<PlayerInventoryData>> PLAYER_DATA = ATTACHMENTS.register(
            "player_inventory",
            () -> AttachmentType.serializable(holder -> new PlayerInventoryData((Player)holder)).build()
    );

    private ModAttachments() {}

    public static void markDirty(Player player) {
        // Entity attachments are persisted with the entity. Delta synchronization is scheduled by ModNetwork.
        com.cappleapple.stacksnotslots.network.ModNetwork.queueInventorySync(player);
    }
}
