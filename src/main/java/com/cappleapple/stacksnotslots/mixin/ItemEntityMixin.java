package com.cappleapple.stacksnotslots.mixin;

import com.cappleapple.stacksnotslots.api.InsertionResult;
import com.cappleapple.stacksnotslots.data.ModAttachments;
import com.cappleapple.stacksnotslots.inventory.InsertionContext;
import com.cappleapple.stacksnotslots.inventory.InventoryTransactions;
import com.cappleapple.stacksnotslots.pickup.PickupFeedback;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ItemEntity.class)
public abstract class ItemEntityMixin {
    @Redirect(method = "playerTouch", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Inventory;add(Lnet/minecraft/world/item/ItemStack;)Z"))
    private boolean sns$capacityPickup(Inventory vanillaInventory, ItemStack stack) {
        if (!vanillaInventory.player.getData(ModAttachments.PLAYER_DATA).migratedVanillaInventory()) return vanillaInventory.add(stack);
        InsertionResult result = InventoryTransactions.insert(vanillaInventory.player, stack, InsertionContext.WORLD_PICKUP, false);
        if (vanillaInventory.player instanceof ServerPlayer serverPlayer) {
            PickupFeedback.notifyRejected(serverPlayer, result, serverPlayer.getData(ModAttachments.PLAYER_DATA).inventory());
        }
        stack.setCount(result.remainder().getCount());
        return result.acceptedAnything();
    }
}
