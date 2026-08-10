package com.cappleapple.stacksnotslots.pickup;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.cappleapple.stacksnotslots.api.CapacityAmount;
import com.cappleapple.stacksnotslots.category.CategoryDefinition;
import com.cappleapple.stacksnotslots.category.CategoryRule;
import com.cappleapple.stacksnotslots.category.SortMode;
import com.cappleapple.stacksnotslots.inventory.DynamicCapacityInventory;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class PickupLimitCalculatorTest {
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }

    @Test
    void overlappingLimitsUseMostRestrictiveAndSupportPartialPickup() {
        DynamicCapacityInventory inventory = new DynamicCapacityInventory(() -> 1_000);
        inventory.insert(new ItemStack(Items.DIAMOND, 4), false);
        CategoryDefinition broad = category("broad", 10, false);
        CategoryDefinition restrictive = category("restrictive", 5, false);
        CategoryDefinition unlimited = category("unlimited", -1, false);
        assertEquals(1, PickupLimitCalculator.maximumAccepted(inventory, List.of(broad, restrictive, unlimited), new ItemStack(Items.DIAMOND, 32), 32));
    }

    @Test
    void exclusionsWinOverIncludes() {
        CategoryDefinition excluded = category("excluded", 5, true);
        DynamicCapacityInventory inventory = new DynamicCapacityInventory(() -> 1_000);
        assertEquals(32, PickupLimitCalculator.maximumAccepted(inventory, List.of(excluded), new ItemStack(Items.DIAMOND, 32), 32));
    }

    @Test
    void categoryLimitsRetainFractionalUsageForLargeStacks() {
        ItemStack diamond = new ItemStack(Items.DIAMOND);
        diamond.set(DataComponents.MAX_STACK_SIZE, 128);
        DynamicCapacityInventory inventory = new DynamicCapacityInventory(() -> 10);
        inventory.insert(diamond, false);
        CategoryDefinition category = category("large_stack", 1, false);

        assertEquals(CapacityAmount.fraction(1, 2),
                PickupLimitCalculator.usageForCategoryExact(inventory, category));
        assertEquals(1, PickupLimitCalculator.maximumAccepted(inventory, List.of(category), diamond.copyWithCount(4), 4));
    }

    private static CategoryDefinition category(String name, long limit, boolean excludeDiamond) {
        ResourceLocation diamond = BuiltInRegistries.ITEM.getKey(Items.DIAMOND);
        return new CategoryDefinition(ResourceLocation.fromNamespaceAndPath("stacksnotslots", name), name, diamond, 0,
                List.of(new CategoryRule(CategoryRule.Type.ITEM, diamond)),
                excludeDiamond ? List.of(new CategoryRule(CategoryRule.Type.ITEM, diamond)) : List.of(), limit,
                SortMode.NAME_ASCENDING, true, false);
    }
}
