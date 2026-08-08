package com.cappleapple.stacksnotslots.network;

import com.cappleapple.stacksnotslots.StacksNotSlots;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record CategoryEditPayload(Operation operation, CompoundTag category) implements CustomPacketPayload {
    public static final Type<CategoryEditPayload> TYPE = new Type<>(StacksNotSlots.id("category_edit"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CategoryEditPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override public CategoryEditPayload decode(RegistryFriendlyByteBuf buffer) {
            int operation = buffer.readUnsignedByte();
            if (operation >= Operation.values().length) throw new IllegalArgumentException("Invalid category operation");
            CompoundTag tag = buffer.readNbt();
            return new CategoryEditPayload(Operation.values()[operation], tag == null ? new CompoundTag() : tag);
        }
        @Override public void encode(RegistryFriendlyByteBuf buffer, CategoryEditPayload payload) {
            buffer.writeByte(payload.operation().ordinal());
            buffer.writeNbt(payload.category());
        }
    };
    public CategoryEditPayload { category = category.copy(); }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public enum Operation { UPSERT, DELETE, RESET }
}
