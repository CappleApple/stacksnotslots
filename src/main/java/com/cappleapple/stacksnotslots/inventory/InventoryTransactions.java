package com.cappleapple.stacksnotslots.inventory;

import com.cappleapple.stacksnotslots.api.InsertionRejection;
import com.cappleapple.stacksnotslots.api.InsertionResult;
import com.cappleapple.stacksnotslots.config.CommonConfig;
import com.cappleapple.stacksnotslots.data.ModAttachments;
import com.cappleapple.stacksnotslots.data.PlayerInventoryData;
import com.cappleapple.stacksnotslots.pickup.PickupLimitCalculator;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public final class InventoryTransactions {
    private InventoryTransactions() {}

    public static InsertionResult insert(Player player, ItemStack stack, InsertionContext context, boolean simulate) {
        PlayerInventoryData data = player.getData(ModAttachments.PLAYER_DATA);
        DynamicCapacityInventory inventory = data.inventory();
        if (context == InsertionContext.WORLD_PICKUP && inventory.isOverCapacity()
                && CommonConfig.OVER_CAPACITY_BLOCKS_PICKUP.getAsBoolean()) {
            return inventory.insert(stack, simulate);
        }
        boolean enforceCategoryLimits = context == InsertionContext.WORLD_PICKUP
                ? CommonConfig.CATEGORY_LIMITS_WORLD_PICKUP.getAsBoolean()
                : context == InsertionContext.MANUAL_TRANSFER && CommonConfig.CATEGORY_LIMITS_MANUAL_TRANSFERS.getAsBoolean();
        int allowed = enforceCategoryLimits
                ? PickupLimitCalculator.maximumAccepted(inventory, data.categories().categories(), stack, stack.getCount())
                : stack.getCount();
        InsertionRejection limitingReason = allowed < stack.getCount() ? InsertionRejection.CATEGORY_LIMIT : InsertionRejection.NONE;

        InsertionResult proposed = limitingReason == InsertionRejection.NONE
                ? inventory.insert(stack, true)
                : inventory.insertAtMost(stack, allowed, limitingReason, true);
        if (context == InsertionContext.WORLD_PICKUP && !CommonConfig.ALLOW_PARTIAL_PICKUP.getAsBoolean() && !proposed.acceptedAll()) {
            InsertionRejection rejection = proposed.rejection() == InsertionRejection.NONE
                    ? InsertionRejection.GLOBAL_CAPACITY
                    : proposed.rejection();
            return new InsertionResult(stack.getCount(), 0, stack.copy(), 0, rejection);
        }
        if (simulate) return proposed;
        return limitingReason == InsertionRejection.NONE
                ? inventory.insert(stack, false)
                : inventory.insertAtMost(stack, allowed, limitingReason, false);
    }
}
