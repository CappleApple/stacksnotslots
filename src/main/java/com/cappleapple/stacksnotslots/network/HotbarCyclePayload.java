package com.cappleapple.stacksnotslots.network;

import com.cappleapple.stacksnotslots.StacksNotSlots;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record HotbarCyclePayload(int slot, int direction) implements CustomPacketPayload {
    public static final Type<HotbarCyclePayload> TYPE = new Type<>(StacksNotSlots.id("hotbar_cycle"));
    public static final StreamCodec<RegistryFriendlyByteBuf, HotbarCyclePayload> STREAM_CODEC = new StreamCodec<>() {
        @Override public HotbarCyclePayload decode(RegistryFriendlyByteBuf buffer) { return new HotbarCyclePayload(buffer.readByte(), buffer.readByte()); }
        @Override public void encode(RegistryFriendlyByteBuf buffer, HotbarCyclePayload payload) { buffer.writeByte(payload.slot()); buffer.writeByte(payload.direction()); }
    };
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
