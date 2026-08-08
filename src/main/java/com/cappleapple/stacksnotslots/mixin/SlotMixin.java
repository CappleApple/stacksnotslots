package com.cappleapple.stacksnotslots.mixin;

import com.cappleapple.stacksnotslots.data.ModAttachments;
import com.cappleapple.stacksnotslots.inventory.CapacityCosts;
import com.cappleapple.stacksnotslots.inventory.DynamicCapacityInventory;
import com.cappleapple.stacksnotslots.config.CommonConfig;
import com.cappleapple.stacksnotslots.pickup.PickupLimitCalculator;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Makes vanilla transfer algorithms split at the remaining-capacity boundary instead of over-inserting. */
@Mixin(Slot.class)
public abstract class SlotMixin {
    @Shadow @Final public Container container;
    @Shadow @Final private int slot;

    @Inject(method = "getMaxStackSize(Lnet/minecraft/world/item/ItemStack;)I", at = @At("HEAD"), cancellable = true)
    private void sns$capacityLimitedStackSize(ItemStack incoming, CallbackInfoReturnable<Integer> callback) {
        if (!(container instanceof Inventory playerInventory)) return;
        var data = playerInventory.player.getData(ModAttachments.PLAYER_DATA);
        if (!data.migratedVanillaInventory() || slot < 0 || slot >= Inventory.INVENTORY_SIZE || incoming.isEmpty()) return;
        DynamicCapacityInventory inventory = data.inventory();
        ItemStack existing = playerInventory.getItem(slot);
        long replaceableCapacity = inventory.remainingCapacity();
        if (!existing.isEmpty()) replaceableCapacity = Math.min(Long.MAX_VALUE, replaceableCapacity + CapacityCosts.cost(existing, existing.getCount()));
        int allowed = (int)Math.min(incoming.getMaxStackSize(), replaceableCapacity / CapacityCosts.unitCost(incoming));
        if (CommonConfig.CATEGORY_LIMITS_MANUAL_TRANSFERS.getAsBoolean()) {
            int additional = PickupLimitCalculator.maximumAccepted(inventory, data.categories().categories(), incoming, incoming.getMaxStackSize());
            int existingCount = !existing.isEmpty() && ItemStack.isSameItemSameComponents(existing, incoming) ? existing.getCount() : 0;
            allowed = Math.min(allowed, existingCount + additional);
        }
        callback.setReturnValue(Math.max(0, allowed));
    }
}
