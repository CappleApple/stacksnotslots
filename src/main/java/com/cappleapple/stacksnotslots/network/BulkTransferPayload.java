package com.cappleapple.stacksnotslots.network;

import com.cappleapple.stacksnotslots.StacksNotSlots;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Requests a server-authoritative bulk move against the open menu or looked-at block inventory. */
public record BulkTransferPayload(Direction direction, Target target) implements CustomPacketPayload {
    public static final Type<BulkTransferPayload> TYPE = new Type<>(StacksNotSlots.id("bulk_transfer"));
    public static final StreamCodec<RegistryFriendlyByteBuf, BulkTransferPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override public BulkTransferPayload decode(RegistryFriendlyByteBuf buffer) {
            int direction = buffer.readUnsignedByte();
            int target = buffer.readUnsignedByte();
            if (direction >= Direction.values().length || target >= Target.values().length) throw new IllegalArgumentException("Invalid bulk transfer");
            return new BulkTransferPayload(Direction.values()[direction], Target.values()[target]);
        }
        @Override public void encode(RegistryFriendlyByteBuf buffer, BulkTransferPayload payload) {
            buffer.writeByte(payload.direction().ordinal());
            buffer.writeByte(payload.target().ordinal());
        }
    };

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public enum Direction { TO_CONTAINER, FROM_CONTAINER }
    public enum Target { OPEN_MENU, LOOKED_AT }
}
