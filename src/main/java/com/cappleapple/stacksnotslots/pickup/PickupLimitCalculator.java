package com.cappleapple.stacksnotslots.pickup;

import com.cappleapple.stacksnotslots.api.LogicalInventoryEntry;
import com.cappleapple.stacksnotslots.category.CategoryDefinition;
import com.cappleapple.stacksnotslots.category.CategoryMatcher;
import com.cappleapple.stacksnotslots.inventory.CapacityCosts;
import com.cappleapple.stacksnotslots.inventory.DynamicCapacityInventory;
import java.util.List;
import net.minecraft.world.item.ItemStack;

public final class PickupLimitCalculator {
    private PickupLimitCalculator() {}

    public static int maximumAccepted(DynamicCapacityInventory inventory, List<CategoryDefinition> categories, ItemStack stack, int requested) {
        long unitCost = CapacityCosts.unitCost(stack);
        int allowed = Math.max(0, requested);
        for (CategoryDefinition category : categories) {
            if (!category.enabled() || category.pickupLimit() < 0 || !CategoryMatcher.matches(category, stack)) continue;
            long categoryUsage = usageForCategory(inventory, category);
            long remaining = Math.max(0, category.pickupLimit() - categoryUsage);
            allowed = Math.min(allowed, (int)Math.min(Integer.MAX_VALUE, remaining / unitCost));
        }
        return allowed;
    }

    public static long usageForCategory(DynamicCapacityInventory inventory, CategoryDefinition category) {
        long used = 0;
        for (LogicalInventoryEntry entry : inventory.entries()) {
            if (!CategoryMatcher.matches(category, entry.representative())) continue;
            long cost = CapacityCosts.cost(entry.representative(), entry.quantity());
            used = cost > Long.MAX_VALUE - used ? Long.MAX_VALUE : used + cost;
        }
        return used;
    }
}
