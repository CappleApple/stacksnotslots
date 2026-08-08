package com.cappleapple.stacksnotslots.mixin;

import com.cappleapple.stacksnotslots.data.ModAttachments;
import com.cappleapple.stacksnotslots.network.ModNetwork;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Gives an open browser native QUICK_MOVE semantics from the visible player grid into backend storage. */
@Mixin(InventoryMenu.class)
public abstract class InventoryMenuMixin {
    @Inject(method = "quickMoveStack", at = @At("HEAD"), cancellable = true)
    private void sns$stowVisibleStack(Player player, int menuIndex, CallbackInfoReturnable<ItemStack> callback) {
        if (!ModNetwork.isBrowserOpen(player)
                || !player.getData(ModAttachments.PLAYER_DATA).migratedVanillaInventory()) return;

        InventoryMenu menu = (InventoryMenu)(Object)this;
        if (menuIndex < 0 || menuIndex >= menu.slots.size()) return;
        Slot sourceSlot = menu.getSlot(menuIndex);
        int inventorySlot = sourceSlot.getContainerSlot();
        if (sourceSlot.container != player.getInventory() || inventorySlot < 0 || inventorySlot >= 36 || !sourceSlot.hasItem()) return;

        ItemStack source = sourceSlot.getItem();
        EquipmentSlot equipmentSlot = player.getEquipmentSlotForItem(source);
        boolean canAutoEquip = equipmentSlot.getType() == EquipmentSlot.Type.HUMANOID_ARMOR
                && !menu.getSlot(8 - equipmentSlot.getIndex()).hasItem();
        if (equipmentSlot == EquipmentSlot.OFFHAND && !menu.getSlot(InventoryMenu.SHIELD_SLOT).hasItem()) canAutoEquip = true;
        if (canAutoEquip) return;

        ItemStack original = source.copy();
        if (!player.getData(ModAttachments.PLAYER_DATA).inventory().stowSyntheticSlot(inventorySlot)) return;
        menu.broadcastChanges();
        callback.setReturnValue(original);
    }
}
