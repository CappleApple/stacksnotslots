package com.cappleapple.stacksnotslots.mixin;

import com.cappleapple.stacksnotslots.data.ModAttachments;
import com.cappleapple.stacksnotslots.inventory.InsertionContext;
import com.cappleapple.stacksnotslots.inventory.InventoryTransactions;
import net.minecraft.core.NonNullList;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Routes shift-click insertion into player storage through the unbounded logical append path. */
@Mixin(AbstractContainerMenu.class)
public abstract class AbstractContainerMenuMixin {
    @Shadow @Final public NonNullList<Slot> slots;

    @Inject(method = "moveItemStackTo", at = @At("HEAD"), cancellable = true)
    private void sns$insertIntoUnifiedPlayerInventory(
            ItemStack source, int startIndex, int endIndex, boolean reverseDirection,
            CallbackInfoReturnable<Boolean> callback
    ) {
        Inventory playerInventory = sns$storageTarget(startIndex, endIndex);
        if (playerInventory == null
                || !playerInventory.player.getData(ModAttachments.PLAYER_DATA).migratedVanillaInventory()
                || source.isEmpty()) return;

        var inventory = playerInventory.player.getData(ModAttachments.PLAYER_DATA).inventory();
        if (inventory.ownsReference(source)) {
            // A player-owned source is an ordinary main-grid/hotbar/equipment move. Let vanilla
            // honor the exact destination range instead of treating it as external insertion.
            return;
        }
        var insertion = InventoryTransactions.insert(playerInventory.player, source, InsertionContext.MANUAL_TRANSFER, false);
        if (!insertion.acceptedAnything()) {
            callback.setReturnValue(false);
            return;
        }
        source.shrink(insertion.acceptedAmount());
        callback.setReturnValue(true);
    }

    @Unique
    private Inventory sns$storageTarget(int startIndex, int endIndex) {
        if (startIndex < 0 || endIndex > slots.size() || startIndex >= endIndex) return null;
        Inventory target = null;
        for (int index = startIndex; index < endIndex; index++) {
            Slot slot = slots.get(index);
            if (!(slot.container instanceof Inventory inventory)
                    || slot.getContainerSlot() < 0
                    || slot.getContainerSlot() >= Inventory.INVENTORY_SIZE) return null;
            if (target != null && target != inventory) return null;
            target = inventory;
        }
        return target;
    }
}
