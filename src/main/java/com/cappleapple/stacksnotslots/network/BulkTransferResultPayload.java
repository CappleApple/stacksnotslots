package com.cappleapple.stacksnotslots.network;

import com.cappleapple.stacksnotslots.StacksNotSlots;
import com.cappleapple.stacksnotslots.inventory.ContainerTransfers;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.ItemStack;

public record BulkTransferResultPayload(BulkTransferPayload.Direction direction, List<ContainerTransfers.TransferredStack> stacks)
        implements CustomPacketPayload {
    public static final int MAX_ENTRIES = 256;
    public static final Type<BulkTransferResultPayload> TYPE = new Type<>(StacksNotSlots.id("bulk_transfer_result"));
    public static final StreamCodec<RegistryFriendlyByteBuf, BulkTransferResultPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override public BulkTransferResultPayload decode(RegistryFriendlyByteBuf buffer) {
            int direction = buffer.readUnsignedByte();
            int count = buffer.readVarInt();
            if (direction >= BulkTransferPayload.Direction.values().length || count < 0 || count > MAX_ENTRIES) {
                throw new IllegalArgumentException("Invalid bulk transfer result");
            }
            ArrayList<ContainerTransfers.TransferredStack> stacks = new ArrayList<>(count);
            for (int index = 0; index < count; index++) {
                ItemStack prototype = ItemStack.STREAM_CODEC.decode(buffer).copyWithCount(1);
                long quantity = buffer.readVarLong();
                if (quantity <= 0) throw new IllegalArgumentException("Invalid transferred quantity");
                stacks.add(new ContainerTransfers.TransferredStack(prototype, quantity));
            }
            return new BulkTransferResultPayload(BulkTransferPayload.Direction.values()[direction], stacks);
        }

        @Override public void encode(RegistryFriendlyByteBuf buffer, BulkTransferResultPayload payload) {
            buffer.writeByte(payload.direction().ordinal());
            int count = Math.min(MAX_ENTRIES, payload.stacks().size());
            buffer.writeVarInt(count);
            for (int index = 0; index < count; index++) {
                ContainerTransfers.TransferredStack stack = payload.stacks().get(index);
                ItemStack.STREAM_CODEC.encode(buffer, stack.prototype());
                buffer.writeVarLong(stack.quantity());
            }
        }
    };

    public BulkTransferResultPayload { stacks = List.copyOf(stacks); }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
