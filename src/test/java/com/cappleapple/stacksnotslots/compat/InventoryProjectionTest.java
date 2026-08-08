package com.cappleapple.stacksnotslots.compat;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.cappleapple.stacksnotslots.category.CategoryDefinition;
import com.cappleapple.stacksnotslots.category.CategoryRule;
import com.cappleapple.stacksnotslots.category.SortMode;
import com.cappleapple.stacksnotslots.data.PlayerInventoryData;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class InventoryProjectionTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void categorySelectionFiltersAndSortsOnlyTheVanillaMainGrid() {
        PlayerInventoryData data = new PlayerInventoryData(null);
        ArrayList<ItemStack> sparse = new ArrayList<>();
        for (int slot = 0; slot < 9; slot++) sparse.add(ItemStack.EMPTY);
        sparse.add(new ItemStack(Items.STONE));
        sparse.add(new ItemStack(Items.APPLE));
        sparse.add(new ItemStack(Items.DIRT));
        data.inventory().loadNetworkSnapshot(sparse, 1);

        ResourceLocation categoryId = ResourceLocation.fromNamespaceAndPath("stacksnotslots", "blocks_test");
        data.categories().replaceAll(List.of(new CategoryDefinition(
                categoryId, "Blocks", BuiltInRegistries.ITEM.getKey(Items.STONE), 0,
                List.of(
                        new CategoryRule(CategoryRule.Type.ITEM, BuiltInRegistries.ITEM.getKey(Items.STONE)),
                        new CategoryRule(CategoryRule.Type.ITEM, BuiltInRegistries.ITEM.getKey(Items.DIRT))),
                List.of(), -1,
                SortMode.REGISTRY_ID, true, false)), true);

        int[] stable = InventoryProjection.build(data);
        assertEquals(9, stable[9]);
        assertEquals(10, stable[10]);
        assertEquals(11, stable[11]);

        data.setSelectedCategoryPreference(categoryId);
        data.setInventorySortPreference(SortMode.REGISTRY_ID);
        int[] filtered = InventoryProjection.build(data);
        assertEquals(11, filtered[9]);
        assertEquals(9, filtered[10]);
        assertEquals(-1, filtered[11]);
        for (int hotbarSlot = 0; hotbarSlot < 9; hotbarSlot++) assertEquals(hotbarSlot, filtered[hotbarSlot]);
    }
}
