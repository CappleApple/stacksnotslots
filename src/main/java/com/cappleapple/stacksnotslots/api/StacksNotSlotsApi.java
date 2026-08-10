package com.cappleapple.stacksnotslots.api;

import com.cappleapple.stacksnotslots.data.ModAttachments;
import com.cappleapple.stacksnotslots.inventory.CapacityCosts;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** Stable public entry point; callers never need to depend on implementation packages. */
public final class StacksNotSlotsApi {
    public static final ResourceLocation INVENTORY_CAPACITY_ATTRIBUTE = com.cappleapple.stacksnotslots.StacksNotSlots.id("inventory_capacity");
    private StacksNotSlotsApi() {}

    public static ICapacityInventory inventory(Player player) {
        return player.getData(ModAttachments.PLAYER_DATA).inventory();
    }

    public static long capacityCost(ItemStack stack) {
        return CapacityCosts.cost(stack, stack.getCount());
    }

    /** Exact capacity cost. Prefer this over the rounded legacy {@link #capacityCost(ItemStack)} view. */
    public static CapacityAmount exactCapacityCost(ItemStack stack) {
        return CapacityCosts.costExact(stack, stack.getCount());
    }

    public static AutoCloseable registerCapacityCostProvider(ResourceLocation id, int priority, CapacityCostProvider provider) {
        return CapacityCosts.register(id, priority, provider);
    }

    public static java.util.List<CategoryView> categories(Player player) {
        return player.getData(ModAttachments.PLAYER_DATA).categories().categories().stream()
                .map(CategoryView::fromDefinition)
                .toList();
    }
}
