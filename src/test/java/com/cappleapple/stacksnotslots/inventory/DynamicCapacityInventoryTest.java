package com.cappleapple.stacksnotslots.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cappleapple.stacksnotslots.api.InsertionRejection;
import java.util.concurrent.atomic.AtomicLong;
import java.util.Map;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class DynamicCapacityInventoryTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void defaultCapacityIncludesMainGridAndHotbar() {
        assertEquals(36, Inventory.INVENTORY_SIZE);
        assertEquals(2_304, CapacityCosts.DEFAULT_INVENTORY_CAPACITY_UNITS);
        assertEquals(Inventory.INVENTORY_SIZE * CapacityCosts.STACK_EQUIVALENT_UNITS,
                CapacityCosts.DEFAULT_INVENTORY_CAPACITY_UNITS);
    }

    @Test
    void exactCapacityAndPartialInsertionAreTransactional() {
        DynamicCapacityInventory inventory = new DynamicCapacityInventory(() -> 64);
        assertEquals(64, inventory.insert(new ItemStack(Items.STONE, 64), true).acceptedAmount());
        assertEquals(0, inventory.usedCapacity());
        assertTrue(inventory.insert(new ItemStack(Items.STONE, 64), false).acceptedAll());
        assertEquals(64, inventory.usedCapacity());

        var rejected = inventory.insert(new ItemStack(Items.DIRT), false);
        assertEquals(InsertionRejection.GLOBAL_CAPACITY, rejected.rejection());
        assertEquals(1, rejected.remainder().getCount());
        assertTrue(inventory.validate());
    }

    @Test
    void stackSizesUseIntegerStackEquivalentCosts() {
        assertEquals(1, CapacityCosts.unitCost(new ItemStack(Items.COBBLESTONE)));
        assertEquals(4, CapacityCosts.unitCost(new ItemStack(Items.ENDER_PEARL)));
        assertEquals(64, CapacityCosts.unitCost(new ItemStack(Items.POTION)));
        assertEquals(64, CapacityCosts.unitCost(new ItemStack(Items.IRON_SWORD)));
        ItemStack moddedStackSize = new ItemStack(Items.STONE);
        moddedStackSize.set(DataComponents.MAX_STACK_SIZE, 32);
        assertEquals(2, CapacityCosts.unitCost(moddedStackSize));

        DynamicCapacityInventory inventory = new DynamicCapacityInventory(() -> 64);
        assertTrue(inventory.insert(new ItemStack(Items.ENDER_PEARL, 16), false).acceptedAll());
        assertEquals(64, inventory.usedCapacity());
    }

    @Test
    void containerTransfersFillMainGridBeforeHotbarAndBackend() {
        DynamicCapacityInventory inventory = new DynamicCapacityInventory(() -> 10_000);
        inventory.replaceSyntheticSlotFromItemUse(9, new ItemStack(Items.DIRT));

        assertTrue(inventory.insertInPlayerTransferOrder(new ItemStack(Items.STONE, 64), false).acceptedAll());
        assertEquals(Items.DIRT, inventory.syntheticStack(9).getItem());
        assertEquals(Items.STONE, inventory.syntheticStack(10).getItem());
        assertTrue(inventory.syntheticStack(0).isEmpty());
        assertTrue(inventory.validate());
    }

    @Test
    void customQuickMoveResultsCanBeReorderedOrStowedWithoutMovingExistingStacks() {
        DynamicCapacityInventory inventory = new DynamicCapacityInventory(() -> 10_000);
        inventory.replaceSyntheticSlotFromItemUse(9, new ItemStack(Items.DIRT, 3));
        List<ItemStack> orderedBefore = inventory.visibleCompatibilitySnapshot();
        inventory.replaceSyntheticSlotFromItemUse(35, new ItemStack(Items.STONE, 7));

        assertTrue(inventory.relocateReceivedVisibleStacks(orderedBefore, false));
        assertEquals(Items.DIRT, inventory.syntheticStack(9).getItem());
        assertEquals(Items.STONE, inventory.syntheticStack(10).getItem());
        assertTrue(inventory.syntheticStack(35).isEmpty());

        List<ItemStack> backendBefore = inventory.visibleCompatibilitySnapshot();
        inventory.replaceSyntheticSlotFromItemUse(10, new ItemStack(Items.STONE, 12));
        assertTrue(inventory.relocateReceivedVisibleStacks(backendBefore, true));
        assertEquals(Items.STONE, inventory.syntheticStack(10).getItem());
        assertEquals(7, inventory.syntheticStack(10).getCount());
        assertEquals(Items.STONE, inventory.syntheticStack(36).getItem());
        assertEquals(5, inventory.syntheticStack(36).getCount());
        assertTrue(inventory.validate());
    }

    @Test
    void nativeMenuQuickMoveStagingRestoresTheVisibleSlotAndKeepsOnlyTheRemainder() {
        DynamicCapacityInventory inventory = new DynamicCapacityInventory(() -> 10_000);
        inventory.replaceSyntheticSlotFromItemUse(9, new ItemStack(Items.DIRT, 5));
        inventory.replaceSyntheticSlotFromItemUse(36, new ItemStack(Items.STONE, 64));

        DynamicCapacityInventory.BackendQuickMoveStage stage =
                inventory.beginBackendQuickMove(new ItemStack(Items.STONE), 9);
        assertEquals(Items.STONE, inventory.syntheticStack(9).getItem());
        assertEquals(64, inventory.syntheticStack(9).getCount());
        inventory.extractSyntheticSlot(9, 40, false);

        assertEquals(40, inventory.finishBackendQuickMove(stage));
        assertEquals(Items.DIRT, inventory.syntheticStack(9).getItem());
        assertEquals(5, inventory.syntheticStack(9).getCount());
        assertEquals(Items.STONE, inventory.syntheticStack(36).getItem());
        assertEquals(24, inventory.syntheticStack(36).getCount());
        assertTrue(inventory.validate());
    }

    @Test
    void capacityChangesAreLiveAndNeverDeleteItems() {
        AtomicLong capacity = new AtomicLong(128);
        DynamicCapacityInventory inventory = new DynamicCapacityInventory(capacity::get);
        inventory.insert(new ItemStack(Items.STONE, 128), false);
        capacity.set(64);
        assertTrue(inventory.isOverCapacity());
        assertEquals(InsertionRejection.OVER_CAPACITY, inventory.insert(new ItemStack(Items.DIRT), false).rejection());
        assertEquals(128, inventory.entries().getFirst().quantity());
        capacity.set(256);
        assertTrue(inventory.insert(new ItemStack(Items.DIRT), false).acceptedAll());
        assertFalse(inventory.isOverCapacity());
    }

    @Test
    void componentsDefineLogicalIdentityAndCompatibleStacksConsolidate() {
        DynamicCapacityInventory inventory = new DynamicCapacityInventory(() -> 512);
        inventory.insert(new ItemStack(Items.STONE, 40), false);
        inventory.insert(new ItemStack(Items.STONE, 40), false);
        assertEquals(1, inventory.entries().size());
        assertEquals(80, inventory.entries().getFirst().quantity());
        assertEquals(2, inventory.syntheticSlotCount());

        ItemStack named = new ItemStack(Items.STONE);
        named.set(DataComponents.CUSTOM_NAME, Component.literal("Different"));
        inventory.insert(named, false);
        assertEquals(2, inventory.entries().size());

        ItemStack damaged = new ItemStack(Items.IRON_PICKAXE);
        damaged.setDamageValue(7);
        inventory.insert(damaged, false);
        ItemStack undamaged = new ItemStack(Items.IRON_PICKAXE);
        inventory.insert(undamaged, false);
        assertNotEquals(ItemStack.hashItemAndComponents(damaged), ItemStack.hashItemAndComponents(undamaged));
        assertEquals(4, inventory.entries().size());
    }

    @Test
    void publicTransactionViewsCannotMutateOwnedStacks() {
        DynamicCapacityInventory inventory = new DynamicCapacityInventory(() -> 128);
        var insertion = inventory.insert(new ItemStack(Items.APPLE, 3), false);
        insertion.remainder().setCount(64);
        ItemStack representative = inventory.entries().getFirst().representative();
        representative.setCount(64);
        var extraction = inventory.extract(new ItemStack(Items.APPLE), 1, true);
        extraction.extractedStacks().getFirst().setCount(64);
        assertEquals(3, inventory.entries().getFirst().quantity());
        assertTrue(inventory.validate());
    }

    @Test
    void committedItemUseReplacementIsPreservedAsOverCapacity() {
        DynamicCapacityInventory inventory = new DynamicCapacityInventory(() -> 64);
        inventory.insert(new ItemStack(Items.ENDER_PEARL), false);
        inventory.insert(new ItemStack(Items.STONE, 60), false);
        assertEquals(64, inventory.usedCapacity());
        assertThrows(IllegalArgumentException.class,
                () -> inventory.replaceSyntheticSlot(0, new ItemStack(Items.POTION)));

        inventory.replaceSyntheticSlotFromItemUse(0, new ItemStack(Items.POTION));
        assertEquals(124, inventory.usedCapacity());
        assertTrue(inventory.isOverCapacity());
        assertEquals(2, inventory.syntheticSlotCount());
        assertTrue(inventory.validate());
    }

    @Test
    void compatibilitySlotsGrowPastArbitraryCeilingsAndCapacityNAllowsNDistinctItems() {
        int capacity = 1_000;
        DynamicCapacityInventory inventory = new DynamicCapacityInventory(() -> capacity);
        for (int i = 0; i < capacity; i++) {
            ItemStack distinct = new ItemStack(Items.PAPER);
            distinct.set(DataComponents.CUSTOM_NAME, Component.literal("entry-" + i));
            assertEquals(64, distinct.getMaxStackSize());
            assertTrue(inventory.insert(distinct, false).acceptedAll());
        }
        assertEquals(capacity, inventory.usedCapacity());
        assertEquals(capacity, inventory.entries().size());
        assertEquals(capacity, inventory.syntheticSlotCount());
        assertEquals(capacity + 1, inventory.compatibilitySlotCount());
        assertEquals(InsertionRejection.GLOBAL_CAPACITY, inventory.insert(new ItemStack(Items.PAPER), false).rejection());
    }

    @Test
    void extractionSimulationAndPersistencePreserveQuantities() {
        DynamicCapacityInventory inventory = new DynamicCapacityInventory(() -> 512);
        inventory.insert(new ItemStack(Items.COAL, 130), false);
        assertEquals(100, inventory.extract(new ItemStack(Items.COAL), 100, true).extractedAmount());
        assertEquals(130, inventory.entries().getFirst().quantity());
        assertEquals(100, inventory.extract(new ItemStack(Items.COAL), 100, false).extractedAmount());
        assertEquals(30, inventory.entries().getFirst().quantity());
        assertEquals(30, inventory.usedCapacity());
        assertTrue(inventory.validate());

        RegistryAccess.Frozen access = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        var saved = inventory.serializeNBT(access);
        DynamicCapacityInventory loaded = new DynamicCapacityInventory(() -> 10);
        loaded.deserializeNBT(access, saved);
        assertEquals(30, loaded.entries().getFirst().quantity());
        assertTrue(loaded.isOverCapacity());
        assertTrue(loaded.validate());
    }

    @Test
    void extractingACompleteBackingStackReleasesItsCapacity() {
        DynamicCapacityInventory inventory = new DynamicCapacityInventory(() -> 64);
        inventory.insert(new ItemStack(Items.DIRT), false);
        assertEquals(1, inventory.usedCapacity());
        assertEquals(1, inventory.extract(new ItemStack(Items.DIRT), 1, false).extractedAmount());
        assertEquals(0, inventory.usedCapacity());
        assertTrue(inventory.entries().isEmpty());
        assertTrue(inventory.validate());
    }

    @Test
    void replacementRebasesAccountingAfterLiveSourceMutation() {
        DynamicCapacityInventory inventory = new DynamicCapacityInventory(() -> 64);
        inventory.insert(new ItemStack(Items.DIRT), false);
        ItemStack liveReference = inventory.vanillaStackReference(0);
        assertTrue(inventory.ownsReference(liveReference));
        assertFalse(inventory.ownsReference(liveReference.copy()));
        liveReference.shrink(1);
        inventory.replaceSyntheticSlotFromItemUse(0, ItemStack.EMPTY);
        assertEquals(0, inventory.usedCapacity());
        assertTrue(inventory.entries().isEmpty());
        assertTrue(inventory.validate());
    }

    @Test
    void legacyProjectionMoveSequenceCannotInflateTheCapacityCache() {
        DynamicCapacityInventory inventory = new DynamicCapacityInventory(() -> 64);
        inventory.insert(new ItemStack(Items.DIRT), false);
        ItemStack sourceReference = inventory.vanillaStackReference(0);
        ItemStack moved = sourceReference.split(1);

        inventory.insert(moved, false);
        inventory.replaceSyntheticSlotFromItemUse(0, ItemStack.EMPTY);

        assertEquals(1, inventory.entries().getFirst().quantity());
        assertEquals(1, inventory.usedCapacity());
        assertTrue(inventory.validate());
    }

    @Test
    void explicitSparsePlacementStaysPutAndSurvivesPersistence() {
        DynamicCapacityInventory inventory = new DynamicCapacityInventory(() -> 512);
        inventory.replaceSyntheticSlot(12, new ItemStack(Items.APPLE, 3));
        inventory.insert(new ItemStack(Items.DIRT), false);

        assertEquals(13, inventory.syntheticSlotCount());
        assertEquals(Items.DIRT, inventory.syntheticStack(0).getItem());
        assertEquals(Items.APPLE, inventory.syntheticStack(12).getItem());
        assertEquals(4, inventory.usedCapacity());

        RegistryAccess.Frozen access = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        DynamicCapacityInventory loaded = new DynamicCapacityInventory(() -> 512);
        loaded.deserializeNBT(access, inventory.serializeNBT(access));
        assertEquals(13, loaded.syntheticSlotCount());
        assertEquals(Items.DIRT, loaded.syntheticStack(0).getItem());
        assertEquals(3, loaded.syntheticStack(12).getCount());
        assertTrue(loaded.validate());
    }

    @Test
    void networkDeltasPermitStableEmptySlots() {
        DynamicCapacityInventory client = new DynamicCapacityInventory(() -> 512);
        client.loadNetworkSnapshot(List.of(ItemStack.EMPTY, new ItemStack(Items.STONE), new ItemStack(Items.DIRT)), 7);
        assertTrue(client.applyNetworkDelta(7, 8, 3, Map.of(1, ItemStack.EMPTY)));
        assertEquals(3, client.syntheticSlotCount());
        assertTrue(client.syntheticStack(1).isEmpty());
        assertEquals(Items.DIRT, client.syntheticStack(2).getItem());
        assertTrue(client.validate());
    }

    @Test
    void backendStowAndPickupExclusionNeverReuseVisibleHotbarSlots() {
        DynamicCapacityInventory inventory = new DynamicCapacityInventory(() -> 512);
        inventory.replaceSyntheticSlot(0, new ItemStack(Items.APPLE, 3));
        inventory.replaceSyntheticSlot(9, new ItemStack(Items.STONE));

        assertTrue(inventory.stowSyntheticSlot(0));
        assertTrue(inventory.syntheticStack(0).isEmpty());
        assertEquals(Items.STONE, inventory.syntheticStack(9).getItem());
        assertEquals(Items.APPLE, inventory.syntheticStack(36).getItem());

        assertTrue(inventory.insertAtOrAfter(new ItemStack(Items.DIRT), 9, false).acceptedAll());
        assertTrue(inventory.syntheticStack(0).isEmpty());
        assertEquals(Items.DIRT, inventory.syntheticStack(10).getItem());
        assertEquals(5, inventory.usedCapacity());
        assertTrue(inventory.validate());
    }

    @Test
    void browserTransferUsesOnlyBackendAndOnlyTheMainGrid() {
        DynamicCapacityInventory inventory = new DynamicCapacityInventory(() -> 512);
        inventory.replaceSyntheticSlot(0, new ItemStack(Items.APPLE));
        inventory.replaceSyntheticSlot(36, new ItemStack(Items.DIRT, 32));

        assertTrue(inventory.moveBackendStackToMain(new ItemStack(Items.DIRT)));
        assertEquals(Items.APPLE, inventory.syntheticStack(0).getItem());
        assertEquals(Items.DIRT, inventory.syntheticStack(9).getItem());
        assertEquals(32, inventory.syntheticStack(9).getCount());
        assertTrue(inventory.syntheticStack(36).isEmpty());
        assertFalse(inventory.moveBackendStackToMain(new ItemStack(Items.APPLE)));
        assertTrue(inventory.validate());
    }

    @Test
    void backendEntriesExcludeEveryVisibleCompatibilityPosition() {
        DynamicCapacityInventory inventory = new DynamicCapacityInventory(() -> 512);
        inventory.replaceSyntheticSlot(0, new ItemStack(Items.APPLE, 2));
        inventory.replaceSyntheticSlot(35, new ItemStack(Items.DIRT, 3));
        inventory.replaceSyntheticSlot(36, new ItemStack(Items.STONE, 4));
        inventory.replaceSyntheticSlot(40, new ItemStack(Items.STONE, 5));

        assertEquals(1, inventory.entriesAtOrAfter(36).size());
        assertEquals(Items.STONE, inventory.entriesAtOrAfter(36).getFirst().representative().getItem());
        assertEquals(9, inventory.entriesAtOrAfter(36).getFirst().quantity());
    }

    @Test
    void liveCompatibilityLookupCanConsumeAProjectileFromBackendOnly() {
        DynamicCapacityInventory inventory = new DynamicCapacityInventory(() -> 512);
        inventory.replaceSyntheticSlot(0, new ItemStack(Items.ARROW, 3));
        inventory.replaceSyntheticSlot(36, new ItemStack(Items.ARROW, 2));

        ItemStack backendArrow = inventory.findLiveStackReference(36, stack -> stack.is(Items.ARROW));
        assertSame(inventory.vanillaStackReference(36), backendArrow);
        backendArrow.shrink(1);
        inventory.reconcileExternalMutations();

        assertEquals(3, inventory.syntheticStack(0).getCount());
        assertEquals(1, inventory.syntheticStack(36).getCount());
        assertEquals(4, inventory.usedCapacity());
        assertTrue(inventory.validate());
    }

    @Test
    void stowingMainGridPreservesAllHotbarPositions() {
        DynamicCapacityInventory inventory = new DynamicCapacityInventory(() -> 512);
        inventory.replaceSyntheticSlot(0, new ItemStack(Items.APPLE));
        inventory.replaceSyntheticSlot(8, new ItemStack(Items.COAL));
        inventory.replaceSyntheticSlot(9, new ItemStack(Items.DIRT));
        inventory.replaceSyntheticSlot(35, new ItemStack(Items.STONE));

        assertTrue(inventory.stowMainGrid());
        assertEquals(Items.APPLE, inventory.syntheticStack(0).getItem());
        assertEquals(Items.COAL, inventory.syntheticStack(8).getItem());
        assertTrue(inventory.syntheticStack(9).isEmpty());
        assertTrue(inventory.syntheticStack(35).isEmpty());
        assertEquals(Items.DIRT, inventory.syntheticStack(36).getItem());
        assertEquals(Items.STONE, inventory.syntheticStack(37).getItem());
        assertTrue(inventory.validate());
    }

    @Test
    void backendExtractionNeverConsumesVisibleCopiesOfTheSameIdentity() {
        DynamicCapacityInventory inventory = new DynamicCapacityInventory(() -> 512);
        inventory.replaceSyntheticSlot(9, new ItemStack(Items.STONE, 7));
        inventory.replaceSyntheticSlot(36, new ItemStack(Items.STONE, 11));

        assertEquals(11, inventory.extractAtOrAfter(new ItemStack(Items.STONE), 64, 36, false).extractedAmount());
        assertEquals(7, inventory.syntheticStack(9).getCount());
        assertTrue(inventory.syntheticStack(36).isEmpty());
        assertTrue(inventory.validate());
    }
}
