package com.cappleapple.stacksnotslots.network;

import com.cappleapple.stacksnotslots.StacksNotSlots;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record RequestFullSyncPayload() implements CustomPacketPayload {
    public static final Type<RequestFullSyncPayload> TYPE = new Type<>(StacksNotSlots.id("request_full_sync"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RequestFullSyncPayload> STREAM_CODEC = StreamCodec.unit(new RequestFullSyncPayload());
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
