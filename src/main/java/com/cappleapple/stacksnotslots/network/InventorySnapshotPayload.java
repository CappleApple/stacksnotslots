package com.cappleapple.stacksnotslots.network;

import com.cappleapple.stacksnotslots.StacksNotSlots;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.ItemStack;

public record InventorySnapshotPayload(UUID snapshotId, long revision, int chunkIndex, int chunkCount, List<ItemStack> stacks)
        implements CustomPacketPayload {
    public static final Type<InventorySnapshotPayload> TYPE = new Type<>(StacksNotSlots.id("inventory_snapshot"));
    public static final StreamCodec<RegistryFriendlyByteBuf, InventorySnapshotPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override public InventorySnapshotPayload decode(RegistryFriendlyByteBuf buffer) {
            UUID id = buffer.readUUID();
            long revision = buffer.readVarLong();
            int index = buffer.readVarInt();
            int count = buffer.readVarInt();
            int stackCount = buffer.readVarInt();
            if (count < 1 || count > ModNetwork.MAX_SNAPSHOT_CHUNKS || index < 0 || index >= count
                    || stackCount < 0 || stackCount > ModNetwork.SNAPSHOT_CHUNK_SIZE) {
                throw new IllegalArgumentException("Invalid inventory snapshot framing");
            }
            ArrayList<ItemStack> stacks = new ArrayList<>(stackCount);
            for (int i = 0; i < stackCount; i++) stacks.add(ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer));
            return new InventorySnapshotPayload(id, revision, index, count, stacks);
        }

        @Override public void encode(RegistryFriendlyByteBuf buffer, InventorySnapshotPayload payload) {
            buffer.writeUUID(payload.snapshotId());
            buffer.writeVarLong(payload.revision());
            buffer.writeVarInt(payload.chunkIndex());
            buffer.writeVarInt(payload.chunkCount());
            buffer.writeVarInt(payload.stacks().size());
            payload.stacks().forEach(stack -> ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, stack));
        }
    };

    public InventorySnapshotPayload { stacks = stacks.stream().map(ItemStack::copy).toList(); }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
