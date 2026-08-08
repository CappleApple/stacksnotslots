package com.cappleapple.stacksnotslots.network;

import com.cappleapple.stacksnotslots.StacksNotSlots;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.ItemStack;

public record InventoryActionPayload(Action action, ItemStack prototype) implements CustomPacketPayload {
    public static final Type<InventoryActionPayload> TYPE = new Type<>(StacksNotSlots.id("inventory_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, InventoryActionPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override public InventoryActionPayload decode(RegistryFriendlyByteBuf buffer) {
            int action = buffer.readUnsignedByte();
            if (action >= Action.values().length) throw new IllegalArgumentException("Invalid inventory action");
            return new InventoryActionPayload(Action.values()[action], ItemStack.STREAM_CODEC.decode(buffer).copyWithCount(1));
        }
        @Override public void encode(RegistryFriendlyByteBuf buffer, InventoryActionPayload payload) {
            buffer.writeByte(payload.action().ordinal());
            ItemStack.STREAM_CODEC.encode(buffer, payload.prototype().copyWithCount(1));
        }
    };

    public InventoryActionPayload { prototype = prototype.copyWithCount(1); }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public enum Action { TAKE_STACK, TAKE_HALF, DROP_ONE, DROP_STACK }
}
