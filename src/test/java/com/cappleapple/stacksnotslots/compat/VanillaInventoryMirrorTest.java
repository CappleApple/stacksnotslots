package com.cappleapple.stacksnotslots.compat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cappleapple.stacksnotslots.inventory.DynamicCapacityInventory;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class VanillaInventoryMirrorTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void publishesLiveVanillaReferencesWithoutCappingTheBackend() {
        DynamicCapacityInventory inventory = new DynamicCapacityInventory(() -> 10_000);
        inventory.replaceSyntheticSlotFromItemUse(0, new ItemStack(Items.STONE, 12));
        inventory.replaceSyntheticSlotFromItemUse(40, new ItemStack(Items.DIAMOND, 3));
        List<ItemStack> vanillaItems = emptyVanillaItems();

        VanillaInventoryMirror.publish(inventory, vanillaItems);

        assertSame(inventory.vanillaStackReference(0), vanillaItems.get(0));
        assertEquals(Items.STONE, vanillaItems.get(0).getItem());
        assertEquals(Inventory.INVENTORY_SIZE, vanillaItems.size());
        assertEquals(41, inventory.syntheticSlotCount());
        assertEquals(Items.DIAMOND, inventory.syntheticStack(40).getItem());
    }

    @Test
    void importsDirectListReplacementsBeforeRefreshingTheView() {
        DynamicCapacityInventory inventory = new DynamicCapacityInventory(() -> 10_000);
        inventory.replaceSyntheticSlotFromItemUse(0, new ItemStack(Items.STONE, 12));
        inventory.replaceSyntheticSlotFromItemUse(1, new ItemStack(Items.DIRT, 4));
        List<ItemStack> vanillaItems = emptyVanillaItems();
        VanillaInventoryMirror.publish(inventory, vanillaItems);

        vanillaItems.set(0, new ItemStack(Items.DIAMOND, 2));
        vanillaItems.set(1, ItemStack.EMPTY);
        VanillaInventoryMirror.reconcileDirectWrites(inventory, vanillaItems);

        assertEquals(Items.DIAMOND, inventory.syntheticStack(0).getItem());
        assertEquals(2, inventory.syntheticStack(0).getCount());
        assertTrue(inventory.syntheticStack(1).isEmpty());
        assertSame(inventory.vanillaStackReference(0), vanillaItems.get(0));
        assertSame(inventory.vanillaStackReference(1), vanillaItems.get(1));
    }

    @Test
    void mutationsThroughPublishedReferencesRemainAuthoritative() {
        DynamicCapacityInventory inventory = new DynamicCapacityInventory(() -> 10_000);
        inventory.replaceSyntheticSlotFromItemUse(0, new ItemStack(Items.STONE, 12));
        List<ItemStack> vanillaItems = emptyVanillaItems();
        VanillaInventoryMirror.publish(inventory, vanillaItems);

        vanillaItems.get(0).setCount(5);
        inventory.reconcileExternalMutations();

        assertEquals(5, inventory.syntheticStack(0).getCount());
        assertEquals(5, inventory.usedCapacity());
    }

    private static List<ItemStack> emptyVanillaItems() {
        ArrayList<ItemStack> items = new ArrayList<>(Inventory.INVENTORY_SIZE);
        for (int slot = 0; slot < Inventory.INVENTORY_SIZE; slot++) items.add(ItemStack.EMPTY);
        return items;
    }
}
