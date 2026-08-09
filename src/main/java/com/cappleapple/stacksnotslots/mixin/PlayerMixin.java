package com.cappleapple.stacksnotslots.mixin;

import com.cappleapple.stacksnotslots.data.ModAttachments;
import net.minecraft.core.NonNullList;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Extends vanilla player item lookups and direct writes across the authoritative dynamic inventory. */
@Mixin(Player.class)
public abstract class PlayerMixin {
    @Inject(method = "getProjectile", at = @At("RETURN"), cancellable = true)
    private void sns$findBackendProjectile(ItemStack weapon, CallbackInfoReturnable<ItemStack> callback) {
        if (!callback.getReturnValue().isEmpty() || !(weapon.getItem() instanceof ProjectileWeaponItem projectileWeapon)) return;
        Player player = (Player)(Object)this;
        var data = player.getData(ModAttachments.PLAYER_DATA);
        if (!data.migratedVanillaInventory()) return;
        ItemStack projectile = data.inventory().findLiveStackReference(
                Inventory.INVENTORY_SIZE, projectileWeapon.getAllSupportedProjectiles(weapon));
        if (!projectile.isEmpty()) callback.setReturnValue(projectile);
    }

    @Redirect(
            method = "setItemSlot",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/core/NonNullList;set(ILjava/lang/Object;)Ljava/lang/Object;")
    )
    private Object sns$setDynamicMainHand(NonNullList<ItemStack> list, int index, Object replacement) {
        Player player = (Player)(Object)this;
        ItemStack stack = (ItemStack)replacement;
        var data = player.getData(ModAttachments.PLAYER_DATA);
        if (list == player.getInventory().items && data.migratedVanillaInventory()) {
            ItemStack previous = data.inventory().syntheticStack(index);
            data.inventory().replaceSyntheticSlotFromItemUse(index, stack);
            return previous;
        }
        return list.set(index, stack);
    }
}
