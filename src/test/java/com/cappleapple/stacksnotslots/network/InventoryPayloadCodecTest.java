package com.cappleapple.stacksnotslots.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.netty.buffer.Unpooled;
import java.util.List;
import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class InventoryPayloadCodecTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void snapshotRoundTripsSparseInventorySlots() {
        UUID snapshotId = UUID.randomUUID();
        InventorySnapshotPayload payload = new InventorySnapshotPayload(
                snapshotId,
                12L,
                0,
                1,
                List.of(new ItemStack(Items.STONE, 3), ItemStack.EMPTY, new ItemStack(Items.DIRT)));

        RegistryFriendlyByteBuf buffer = createBuffer();
        try {
            InventorySnapshotPayload.STREAM_CODEC.encode(buffer, payload);
            buffer.readerIndex(0);
            InventorySnapshotPayload decoded = InventorySnapshotPayload.STREAM_CODEC.decode(buffer);

            assertEquals(snapshotId, decoded.snapshotId());
            assertEquals(12L, decoded.revision());
            assertEquals(3, decoded.stacks().size());
            assertSame(Items.STONE, decoded.stacks().get(0).getItem());
            assertEquals(3, decoded.stacks().get(0).getCount());
            assertTrue(decoded.stacks().get(1).isEmpty());
            assertSame(Items.DIRT, decoded.stacks().get(2).getItem());
        } finally {
            buffer.release();
        }
    }

    @Test
    void deltaRoundTripsSlotBeingEmptied() {
        InventoryDeltaPayload payload = new InventoryDeltaPayload(
                12L,
                13L,
                3,
                List.of(
                        new InventoryDeltaPayload.SlotChange(1, ItemStack.EMPTY),
                        new InventoryDeltaPayload.SlotChange(2, new ItemStack(Items.DIRT, 4))));

        RegistryFriendlyByteBuf buffer = createBuffer();
        try {
            InventoryDeltaPayload.STREAM_CODEC.encode(buffer, payload);
            buffer.readerIndex(0);
            InventoryDeltaPayload decoded = InventoryDeltaPayload.STREAM_CODEC.decode(buffer);

            assertEquals(12L, decoded.baseRevision());
            assertEquals(13L, decoded.revision());
            assertEquals(3, decoded.resultingSize());
            assertEquals(2, decoded.changes().size());
            assertEquals(1, decoded.changes().get(0).index());
            assertTrue(decoded.changes().get(0).stack().isEmpty());
            assertEquals(2, decoded.changes().get(1).index());
            assertSame(Items.DIRT, decoded.changes().get(1).stack().getItem());
            assertEquals(4, decoded.changes().get(1).stack().getCount());
        } finally {
            buffer.release();
        }
    }

    @Test
    void stowAndPickupPreferencePayloadsRoundTrip() {
        RegistryFriendlyByteBuf buffer = createBuffer();
        try {
            StowSlotPayload.STREAM_CODEC.encode(buffer, new StowSlotPayload(-1));
            PickupToHotbarPayload.STREAM_CODEC.encode(buffer, new PickupToHotbarPayload(false));
            buffer.readerIndex(0);
            assertEquals(-1, StowSlotPayload.STREAM_CODEC.decode(buffer).slot());
            assertTrue(!PickupToHotbarPayload.STREAM_CODEC.decode(buffer).enabled());
        } finally {
            buffer.release();
        }
    }

    @Test
    void browserTransferIdentityRoundTripsWithoutQuantity() {
        RegistryFriendlyByteBuf buffer = createBuffer();
        try {
            BrowserTransferPayload.STREAM_CODEC.encode(buffer, new BrowserTransferPayload(new ItemStack(Items.DIRT, 32)));
            buffer.readerIndex(0);
            BrowserTransferPayload decoded = BrowserTransferPayload.STREAM_CODEC.decode(buffer);
            assertSame(Items.DIRT, decoded.prototype().getItem());
            assertEquals(1, decoded.prototype().getCount());
        } finally {
            buffer.release();
        }
    }

    private static RegistryFriendlyByteBuf createBuffer() {
        RegistryAccess.Frozen access = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        return new RegistryFriendlyByteBuf(Unpooled.buffer(), access);
    }
}
