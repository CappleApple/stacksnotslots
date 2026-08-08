package com.cappleapple.stacksnotslots.compat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    void explicitViewIsOneShotDistinctAndNeverTouchesHotbar() {
        PlayerInventoryData data = new PlayerInventoryData(null);
        ArrayList<ItemStack> sparse = new ArrayList<>();
        sparse.add(new ItemStack(Items.DIAMOND_SWORD));
        for (int slot = 1; slot < 9; slot++) sparse.add(ItemStack.EMPTY);
        sparse.add(new ItemStack(Items.STONE, 64));
        sparse.add(new ItemStack(Items.STONE, 64));
        sparse.add(new ItemStack(Items.APPLE));
        sparse.add(new ItemStack(Items.DIRT));
        sparse.add(new ItemStack(Items.COBBLESTONE));
        data.inventory().loadNetworkSnapshot(sparse, 1);

        ResourceLocation categoryId = ResourceLocation.fromNamespaceAndPath("stacksnotslots", "blocks_test");
        data.categories().replaceAll(List.of(new CategoryDefinition(
                categoryId, "Blocks", BuiltInRegistries.ITEM.getKey(Items.STONE), 0,
                List.of(
                        new CategoryRule(CategoryRule.Type.ITEM, BuiltInRegistries.ITEM.getKey(Items.STONE)),
                        new CategoryRule(CategoryRule.Type.ITEM, BuiltInRegistries.ITEM.getKey(Items.DIRT)),
                        new CategoryRule(CategoryRule.Type.ITEM, BuiltInRegistries.ITEM.getKey(Items.COBBLESTONE))),
                List.of(), -1, SortMode.REGISTRY_ID, true, false)), true);
        data.setSelectedCategoryPreference(categoryId);
        data.setInventorySortPreference(SortMode.REGISTRY_ID);

        InventoryProjection.applyExplicitView(data);
        assertEquals(Items.DIAMOND_SWORD, data.inventory().syntheticStack(0).getItem());
        assertEquals(Items.COBBLESTONE, data.inventory().syntheticStack(9).getItem());
        assertEquals(Items.DIRT, data.inventory().syntheticStack(10).getItem());
        assertEquals(Items.STONE, data.inventory().syntheticStack(11).getItem());
        assertTrue(data.inventory().syntheticStack(12).isEmpty());
        assertEquals(Items.STONE, data.inventory().syntheticStack(36).getItem());
        assertEquals(Items.APPLE, data.inventory().syntheticStack(37).getItem());

        data.inventory().replaceSyntheticSlotFromItemUse(20, new ItemStack(Items.DIAMOND));
        int[] stable = InventoryProjection.build(data);
        assertEquals(20, stable[20]);
        assertEquals(Items.DIAMOND, data.inventory().syntheticStack(20).getItem());
        assertEquals(Items.DIAMOND_SWORD, data.inventory().syntheticStack(0).getItem());
        assertTrue(data.inventory().validate());
    }
}
