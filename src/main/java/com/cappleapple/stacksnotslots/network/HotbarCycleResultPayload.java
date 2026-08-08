package com.cappleapple.stacksnotslots.network;

import com.cappleapple.stacksnotslots.StacksNotSlots;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.ItemStack;

public record HotbarCycleResultPayload(String bindingName, ItemStack selected) implements CustomPacketPayload {
    public static final Type<HotbarCycleResultPayload> TYPE = new Type<>(StacksNotSlots.id("hotbar_cycle_result"));
    public static final StreamCodec<RegistryFriendlyByteBuf, HotbarCycleResultPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override public HotbarCycleResultPayload decode(RegistryFriendlyByteBuf buffer) {
            return new HotbarCycleResultPayload(buffer.readUtf(64), ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer));
        }
        @Override public void encode(RegistryFriendlyByteBuf buffer, HotbarCycleResultPayload payload) {
            buffer.writeUtf(payload.bindingName(), 64);
            ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, payload.selected());
        }
    };
    public HotbarCycleResultPayload { selected = selected.copy(); }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
