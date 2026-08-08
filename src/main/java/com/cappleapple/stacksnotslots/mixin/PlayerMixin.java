package com.cappleapple.stacksnotslots.mixin;

import com.cappleapple.stacksnotslots.data.ModAttachments;
import net.minecraft.core.NonNullList;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Routes Player#setItemSlot's direct main-hand list write into the authoritative dynamic inventory. */
@Mixin(Player.class)
public abstract class PlayerMixin {
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
