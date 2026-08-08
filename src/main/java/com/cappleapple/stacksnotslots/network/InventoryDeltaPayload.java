package com.cappleapple.stacksnotslots.network;

import com.cappleapple.stacksnotslots.StacksNotSlots;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.ItemStack;

public record InventoryDeltaPayload(long baseRevision, long revision, int resultingSize, List<SlotChange> changes)
        implements CustomPacketPayload {
    public static final Type<InventoryDeltaPayload> TYPE = new Type<>(StacksNotSlots.id("inventory_delta"));
    public static final StreamCodec<RegistryFriendlyByteBuf, InventoryDeltaPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override public InventoryDeltaPayload decode(RegistryFriendlyByteBuf buffer) {
            long base = buffer.readVarLong();
            long revision = buffer.readVarLong();
            int size = buffer.readVarInt();
            int count = buffer.readVarInt();
            if (size < 0 || count < 0 || count > ModNetwork.MAX_DELTA_CHANGES) throw new IllegalArgumentException("Invalid inventory delta");
            ArrayList<SlotChange> changes = new ArrayList<>(count);
            for (int i = 0; i < count; i++) changes.add(new SlotChange(buffer.readVarInt(), ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer)));
            return new InventoryDeltaPayload(base, revision, size, changes);
        }

        @Override public void encode(RegistryFriendlyByteBuf buffer, InventoryDeltaPayload payload) {
            buffer.writeVarLong(payload.baseRevision());
            buffer.writeVarLong(payload.revision());
            buffer.writeVarInt(payload.resultingSize());
            buffer.writeVarInt(payload.changes().size());
            for (SlotChange change : payload.changes()) {
                buffer.writeVarInt(change.index());
                ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, change.stack());
            }
        }
    };

    public InventoryDeltaPayload { changes = List.copyOf(changes); }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public record SlotChange(int index, ItemStack stack) {
        public SlotChange { stack = stack.copy(); }
    }
}
