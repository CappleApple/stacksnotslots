package com.cappleapple.stacksnotslots.network;

import com.cappleapple.stacksnotslots.StacksNotSlots;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record PlayerMetadataPayload(CompoundTag data) implements CustomPacketPayload {
    public static final Type<PlayerMetadataPayload> TYPE = new Type<>(StacksNotSlots.id("player_metadata"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PlayerMetadataPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override public PlayerMetadataPayload decode(RegistryFriendlyByteBuf buffer) {
            CompoundTag tag = buffer.readNbt();
            if (tag == null) throw new IllegalArgumentException("Missing player metadata");
            return new PlayerMetadataPayload(tag);
        }
        @Override public void encode(RegistryFriendlyByteBuf buffer, PlayerMetadataPayload payload) { buffer.writeNbt(payload.data()); }
    };

    public PlayerMetadataPayload { data = data.copy(); }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
