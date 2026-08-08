package com.cappleapple.stacksnotslots.hotbar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cappleapple.stacksnotslots.category.CategoryDefinition;
import com.cappleapple.stacksnotslots.category.CategoryRule;
import com.cappleapple.stacksnotslots.category.PlayerCategoryData;
import com.cappleapple.stacksnotslots.category.SortMode;
import com.cappleapple.stacksnotslots.inventory.DynamicCapacityInventory;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class HotbarBindingsTest {
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }

    @Test
    void categoryCycleSwapsOnlyTheRequestedHotbarSlotOnKeyPress() {
        DynamicCapacityInventory inventory = new DynamicCapacityInventory(() -> 512);
        inventory.replaceSyntheticSlot(0, new ItemStack(Items.COBBLESTONE, 8));
        inventory.replaceSyntheticSlot(1, new ItemStack(Items.APPLE));
        inventory.replaceSyntheticSlot(9, new ItemStack(Items.STONE, 8));
        PlayerCategoryData categories = buildingCategories();
        ResourceLocation categoryId = categories.categories().getFirst().id();
        HotbarBindings hotbar = new HotbarBindings();
        hotbar.set(0, new HotbarBinding(BindingType.CATEGORY, categoryId, null));

        ItemStack cycled = hotbar.cycle(0, 1, inventory, categories);
        assertEquals(Items.STONE, cycled.getItem());
        assertEquals(Items.STONE, inventory.syntheticStack(0).getItem());
        assertEquals(Items.COBBLESTONE, inventory.syntheticStack(9).getItem());
        assertEquals(Items.APPLE, inventory.syntheticStack(1).getItem());

        inventory.replaceSyntheticSlot(0, new ItemStack(Items.DIAMOND));
        assertEquals(Items.DIAMOND, inventory.syntheticStack(0).getItem());
        assertEquals(BindingType.CATEGORY, hotbar.get(0).type());
        assertEquals(Items.COBBLESTONE, hotbar.cycle(0, 1, inventory, categories).getItem());
        assertEquals(Items.APPLE, inventory.syntheticStack(1).getItem());
    }

    @Test
    void exactItemBindingsAreDiscardedAndCannotLockAHotbarSlot() {
        HotbarBindings hotbar = new HotbarBindings();
        hotbar.set(0, new HotbarBinding(BindingType.ITEM, BuiltInRegistries.ITEM.getKey(Items.APPLE), null));
        assertEquals(BindingType.EMPTY, hotbar.get(0).type());
    }

    @Test
    void categoryMemoryDistinguishesComponentsAndSurvivesPersistence() {
        DynamicCapacityInventory inventory = new DynamicCapacityInventory(() -> 256);
        ItemStack first = new ItemStack(Items.PAPER);
        first.set(DataComponents.CUSTOM_NAME, Component.literal("A"));
        ItemStack second = new ItemStack(Items.PAPER);
        second.set(DataComponents.CUSTOM_NAME, Component.literal("B"));
        inventory.replaceSyntheticSlot(0, first);
        inventory.replaceSyntheticSlot(9, second);

        ResourceLocation paper = BuiltInRegistries.ITEM.getKey(Items.PAPER);
        ResourceLocation categoryId = ResourceLocation.fromNamespaceAndPath("stacksnotslots", "papers");
        PlayerCategoryData categories = new PlayerCategoryData();
        categories.replaceAll(List.of(new CategoryDefinition(categoryId, "Papers", paper, 0,
                List.of(new CategoryRule(CategoryRule.Type.ITEM, paper)), List.of(), -1,
                SortMode.NAME_ASCENDING, true, false)), true);
        HotbarBindings hotbar = new HotbarBindings();
        hotbar.set(0, new HotbarBinding(BindingType.CATEGORY, categoryId, null));
        ItemStack cycled = hotbar.cycle(0, 1, inventory, categories);
        assertTrue(ItemStack.isSameItemSameComponents(second, cycled));

        RegistryAccess.Frozen access = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        HotbarBindings loaded = new HotbarBindings();
        loaded.load(access, hotbar.save(access));
        assertTrue(loaded.get(0).selectedEntry().matches(cycled));
        assertEquals(BindingType.CATEGORY, loaded.get(0).type());
    }

    private static PlayerCategoryData buildingCategories() {
        PlayerCategoryData categories = new PlayerCategoryData();
        ResourceLocation categoryId = ResourceLocation.fromNamespaceAndPath("stacksnotslots", "building");
        categories.replaceAll(List.of(new CategoryDefinition(categoryId, "Building", BuiltInRegistries.ITEM.getKey(Items.STONE), 0,
                List.of(new CategoryRule(CategoryRule.Type.ITEM, BuiltInRegistries.ITEM.getKey(Items.COBBLESTONE)),
                        new CategoryRule(CategoryRule.Type.ITEM, BuiltInRegistries.ITEM.getKey(Items.STONE))),
                List.of(), -1, SortMode.NAME_ASCENDING, true, false)), true);
        return categories;
    }
}
