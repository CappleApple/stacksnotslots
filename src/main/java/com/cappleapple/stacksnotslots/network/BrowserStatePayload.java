package com.cappleapple.stacksnotslots.network;

import com.cappleapple.stacksnotslots.StacksNotSlots;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Session-only browser state used to select native quick-move destinations on the server. */
public record BrowserStatePayload(boolean open) implements CustomPacketPayload {
    public static final Type<BrowserStatePayload> TYPE = new Type<>(StacksNotSlots.id("browser_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf, BrowserStatePayload> STREAM_CODEC = new StreamCodec<>() {
        @Override public BrowserStatePayload decode(RegistryFriendlyByteBuf buffer) { return new BrowserStatePayload(buffer.readBoolean()); }
        @Override public void encode(RegistryFriendlyByteBuf buffer, BrowserStatePayload payload) { buffer.writeBoolean(payload.open()); }
    };

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
