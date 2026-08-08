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
    void categoryBindingsCycleAndRecoverWhenSelectionDisappears() {
        DynamicCapacityInventory inventory = new DynamicCapacityInventory(() -> 512);
        inventory.insert(new ItemStack(Items.COBBLESTONE, 8), false);
        inventory.insert(new ItemStack(Items.STONE, 8), false);
        PlayerCategoryData categories = new PlayerCategoryData();
        ResourceLocation categoryId = ResourceLocation.fromNamespaceAndPath("stacksnotslots", "building");
        categories.replaceAll(List.of(new CategoryDefinition(categoryId, "Building", BuiltInRegistries.ITEM.getKey(Items.STONE), 0,
                List.of(new CategoryRule(CategoryRule.Type.ITEM, BuiltInRegistries.ITEM.getKey(Items.COBBLESTONE)),
                        new CategoryRule(CategoryRule.Type.ITEM, BuiltInRegistries.ITEM.getKey(Items.STONE))),
                List.of(), -1, SortMode.NAME_ASCENDING, true, false)), true);
        HotbarBindings hotbar = new HotbarBindings();
        hotbar.set(0, new HotbarBinding(BindingType.CATEGORY, categoryId, null));
        hotbar.set(1, new HotbarBinding(BindingType.CATEGORY, categoryId, null));

        ItemStack first = hotbar.resolve(0, inventory, categories);
        ItemStack cycled = hotbar.cycle(0, 1, inventory, categories);
        assertTrue(!ItemStack.isSameItemSameComponents(first, cycled));
        assertEquals(first.getItem(), hotbar.resolve(1, inventory, categories).getItem());
        inventory.extract(cycled, Integer.MAX_VALUE, false);
        assertEquals(first.getItem(), hotbar.resolve(0, inventory, categories).getItem());
    }

    @Test
    void exactBindingOwnsNoStack() {
        DynamicCapacityInventory inventory = new DynamicCapacityInventory(() -> 64);
        inventory.insert(new ItemStack(Items.APPLE, 3), false);
        HotbarBindings hotbar = new HotbarBindings();
        hotbar.set(0, new HotbarBinding(BindingType.ITEM, BuiltInRegistries.ITEM.getKey(Items.APPLE), null));
        assertEquals(3, hotbar.resolve(0, inventory, new PlayerCategoryData()).getCount());
        assertEquals(3, inventory.entries().getFirst().quantity());
    }

    @Test
    void categoryMemoryDistinguishesComponentsAndSurvivesPersistence() {
        DynamicCapacityInventory inventory = new DynamicCapacityInventory(() -> 128);
        ItemStack first = new ItemStack(Items.PAPER);
        first.set(DataComponents.CUSTOM_NAME, Component.literal("A"));
        ItemStack second = new ItemStack(Items.PAPER);
        second.set(DataComponents.CUSTOM_NAME, Component.literal("B"));
        inventory.insert(first, false);
        inventory.insert(second, false);

        ResourceLocation paper = BuiltInRegistries.ITEM.getKey(Items.PAPER);
        ResourceLocation categoryId = ResourceLocation.fromNamespaceAndPath("stacksnotslots", "papers");
        PlayerCategoryData categories = new PlayerCategoryData();
        categories.replaceAll(List.of(new CategoryDefinition(categoryId, "Papers", paper, 0,
                List.of(new CategoryRule(CategoryRule.Type.ITEM, paper)), List.of(), -1,
                SortMode.NAME_ASCENDING, true, false)), true);
        HotbarBindings hotbar = new HotbarBindings();
        hotbar.set(0, new HotbarBinding(BindingType.CATEGORY, categoryId, null));
        ItemStack initiallySelected = hotbar.resolve(0, inventory, categories);
        ItemStack cycled = hotbar.cycle(0, 1, inventory, categories);
        assertTrue(!ItemStack.isSameItemSameComponents(initiallySelected, cycled));

        RegistryAccess.Frozen access = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        HotbarBindings loaded = new HotbarBindings();
        loaded.load(access, hotbar.save(access));
        assertTrue(ItemStack.isSameItemSameComponents(cycled, loaded.resolve(0, inventory, categories)));
    }
}
