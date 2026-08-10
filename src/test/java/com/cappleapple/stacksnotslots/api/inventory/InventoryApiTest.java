package com.cappleapple.stacksnotslots.api.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cappleapple.stacksnotslots.api.InsertionRejection;
import com.cappleapple.stacksnotslots.api.StacksNotSlotsApi;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.SharedConstants;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class InventoryApiTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void factoryRulesCountsAndChangeCallbacksArePublicApiBehavior() throws Exception {
        AtomicInteger changes = new AtomicInteger();
        MutableCapacityInventory inventory = StacksNotSlotsApi.createInventory(InventoryOptions.builder(() -> 64)
                .insertionRule(stack -> !stack.is(Items.BARRIER))
                .extractionRule(stack -> !stack.is(Items.BEDROCK))
                .mutationListener(changes::incrementAndGet)
                .build());
        AtomicInteger listenerCalls = new AtomicInteger();
        try (AutoCloseable ignored = inventory.addListener((changed, revision) -> listenerCalls.incrementAndGet())) {
            assertEquals(InsertionRejection.INVENTORY_RULE,
                    inventory.insert(new ItemStack(Items.BARRIER), false).rejection());
            assertTrue(inventory.insert(new ItemStack(Items.APPLE, 8), false).acceptedAll());
            assertEquals(8, inventory.count(new ItemStack(Items.APPLE)));
            assertEquals(8, inventory.totalItemCount());
        }
        assertEquals(1, changes.get());
        assertEquals(1, listenerCalls.get());
    }

    @Test
    void transferSnapshotAndPersistencePreserveComponentsWithoutDuplication() {
        MutableCapacityInventory source = InventoryFactory.create(256);
        MutableCapacityInventory destination = InventoryFactory.create(256);
        ItemStack named = new ItemStack(Items.DIAMOND, 12);
        named.set(DataComponents.CUSTOM_NAME, Component.literal("API identity"));
        source.insert(named, false);

        InventoryTransfer.Result simulated = InventoryTransfer.move(source, destination, named, 7, true);
        assertEquals(7, simulated.moved());
        assertEquals(12, source.totalItemCount());
        assertEquals(0, destination.totalItemCount());

        InventoryTransfer.Result committed = InventoryTransfer.move(source, destination, named, 7, false);
        assertEquals(7, committed.moved());
        assertTrue(committed.unresolvedRemainders().isEmpty());
        assertEquals(5, source.totalItemCount());
        assertEquals(7, destination.totalItemCount());
        assertEquals("API identity", destination.entries().getFirst().representative()
                .get(DataComponents.CUSTOM_NAME).getString());

        MutableCapacityInventory snapshotCopy = InventoryFactory.create(256);
        snapshotCopy.applySnapshot(destination.snapshot());
        assertEquals(7, snapshotCopy.totalItemCount());

        RegistryAccess.Frozen access = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        MutableCapacityInventory persisted = InventoryFactory.create(256);
        persisted.deserializeNBT(access, destination.serializeNBT(access));
        assertEquals(7, persisted.totalItemCount());
        assertFalse(persisted.entries().getFirst().representative().isEmpty());
    }
}
