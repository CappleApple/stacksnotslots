package com.cappleapple.stacksnotslots.network;

import com.cappleapple.stacksnotslots.StacksNotSlots;
import com.cappleapple.stacksnotslots.category.SortMode;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/** Client intent for persistent, non-gameplay inventory view preferences. */
public record InventoryViewPreferencesPayload(SortMode sortMode, @Nullable ResourceLocation selectedCategory)
        implements CustomPacketPayload {
    public static final Type<InventoryViewPreferencesPayload> TYPE = new Type<>(StacksNotSlots.id("inventory_view_preferences"));
    public static final StreamCodec<RegistryFriendlyByteBuf, InventoryViewPreferencesPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public InventoryViewPreferencesPayload decode(RegistryFriendlyByteBuf buffer) {
            int sort = buffer.readUnsignedByte();
            if (sort >= SortMode.values().length) throw new IllegalArgumentException("Invalid inventory sort mode");
            ResourceLocation category = buffer.readBoolean() ? ResourceLocation.STREAM_CODEC.decode(buffer) : null;
            return new InventoryViewPreferencesPayload(SortMode.values()[sort], category);
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buffer, InventoryViewPreferencesPayload payload) {
            buffer.writeByte(payload.sortMode().ordinal());
            buffer.writeBoolean(payload.selectedCategory() != null);
            if (payload.selectedCategory() != null) ResourceLocation.STREAM_CODEC.encode(buffer, payload.selectedCategory());
        }
    };

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
