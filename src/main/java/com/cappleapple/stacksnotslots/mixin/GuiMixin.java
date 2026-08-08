package com.cappleapple.stacksnotslots.mixin;

import com.cappleapple.stacksnotslots.data.ModAttachments;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.core.NonNullList;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Makes the HUD hotbar read the live dynamic projection instead of vanilla's stale items list. */
@Mixin(Gui.class)
public abstract class GuiMixin {
    @Shadow @Final private Minecraft minecraft;

    @Redirect(
            method = "renderItemHotbar",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/core/NonNullList;get(I)Ljava/lang/Object;")
    )
    private Object sns$liveHotbarStack(NonNullList<ItemStack> vanillaItems, int slot) {
        if (minecraft.getCameraEntity() instanceof Player player
                && player.getInventory().items == vanillaItems
                && player.getData(ModAttachments.PLAYER_DATA).migratedVanillaInventory()) {
            return player.getInventory().getItem(slot);
        }
        return vanillaItems.get(slot);
    }
}
