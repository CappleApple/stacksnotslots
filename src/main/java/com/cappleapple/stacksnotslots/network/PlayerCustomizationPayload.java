package com.cappleapple.stacksnotslots.network;

import com.cappleapple.stacksnotslots.StacksNotSlots;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Uploads the local player's client-owned tabs and inventory-view preferences. */
public record PlayerCustomizationPayload(CompoundTag data) implements CustomPacketPayload {
    public static final Type<PlayerCustomizationPayload> TYPE = new Type<>(StacksNotSlots.id("player_customization"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PlayerCustomizationPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public PlayerCustomizationPayload decode(RegistryFriendlyByteBuf buffer) {
            CompoundTag tag = buffer.readNbt();
            if (tag == null) throw new IllegalArgumentException("Missing player customization");
            return new PlayerCustomizationPayload(tag);
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buffer, PlayerCustomizationPayload payload) {
            buffer.writeNbt(payload.data());
        }
    };

    public PlayerCustomizationPayload {
        data = data.copy();
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
