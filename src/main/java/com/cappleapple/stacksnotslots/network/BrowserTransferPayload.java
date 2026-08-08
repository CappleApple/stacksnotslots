package com.cappleapple.stacksnotslots.network;

import com.cappleapple.stacksnotslots.StacksNotSlots;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.ItemStack;

public record BrowserTransferPayload(ItemStack prototype) implements CustomPacketPayload {
    public static final Type<BrowserTransferPayload> TYPE = new Type<>(StacksNotSlots.id("browser_transfer"));
    public static final StreamCodec<RegistryFriendlyByteBuf, BrowserTransferPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override public BrowserTransferPayload decode(RegistryFriendlyByteBuf buffer) {
            return new BrowserTransferPayload(ItemStack.STREAM_CODEC.decode(buffer).copyWithCount(1));
        }

        @Override public void encode(RegistryFriendlyByteBuf buffer, BrowserTransferPayload payload) {
            ItemStack.STREAM_CODEC.encode(buffer, payload.prototype().copyWithCount(1));
        }
    };

    public BrowserTransferPayload { prototype = prototype.copyWithCount(1); }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
