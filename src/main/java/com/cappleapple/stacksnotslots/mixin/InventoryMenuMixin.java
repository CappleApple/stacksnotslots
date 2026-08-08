package com.cappleapple.stacksnotslots.mixin;

import com.cappleapple.stacksnotslots.compat.UnifiedInventoryMenuPolicy;
import com.cappleapple.stacksnotslots.data.ModAttachments;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Prevents vanilla from transferring an item between two indices that view the same logical collection. */
@Mixin(InventoryMenu.class)
public abstract class InventoryMenuMixin {
    @Inject(method = "quickMoveStack", at = @At("HEAD"), cancellable = true)
    private void sns$suppressInternalStorageMove(Player player, int menuIndex, CallbackInfoReturnable<ItemStack> callback) {
        if (!player.getData(ModAttachments.PLAYER_DATA).migratedVanillaInventory()
                || menuIndex < UnifiedInventoryMenuPolicy.MAIN_STORAGE_START
                || menuIndex >= UnifiedInventoryMenuPolicy.HOTBAR_END) return;

        InventoryMenu menu = (InventoryMenu)(Object)this;
        ItemStack source = menu.getSlot(menuIndex).getItem();
        EquipmentSlot equipmentSlot = player.getEquipmentSlotForItem(source);
        boolean canAutoEquip = equipmentSlot.getType() == EquipmentSlot.Type.HUMANOID_ARMOR
                && !menu.getSlot(8 - equipmentSlot.getIndex()).hasItem();
        if (equipmentSlot == EquipmentSlot.OFFHAND && !menu.getSlot(InventoryMenu.SHIELD_SLOT).hasItem()) {
            canAutoEquip = true;
        }
        if (UnifiedInventoryMenuPolicy.suppressStorageToStorageShiftClick(menuIndex, canAutoEquip)) {
            callback.setReturnValue(ItemStack.EMPTY);
        }
    }
}
