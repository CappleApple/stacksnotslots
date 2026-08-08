package com.cappleapple.stacksnotslots.network;

import com.cappleapple.stacksnotslots.StacksNotSlots;
import com.cappleapple.stacksnotslots.api.InsertionRejection;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record PickupFeedbackPayload(InsertionRejection reason, long usedCapacity, long capacity) implements CustomPacketPayload {
    public static final Type<PickupFeedbackPayload> TYPE = new Type<>(StacksNotSlots.id("pickup_feedback"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PickupFeedbackPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override public PickupFeedbackPayload decode(RegistryFriendlyByteBuf buffer) {
            int reason = buffer.readUnsignedByte();
            if (reason >= InsertionRejection.values().length) throw new IllegalArgumentException("Invalid pickup feedback");
            return new PickupFeedbackPayload(InsertionRejection.values()[reason], buffer.readVarLong(), buffer.readVarLong());
        }
        @Override public void encode(RegistryFriendlyByteBuf buffer, PickupFeedbackPayload payload) {
            buffer.writeByte(payload.reason().ordinal()); buffer.writeVarLong(payload.usedCapacity()); buffer.writeVarLong(payload.capacity());
        }
    };
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
