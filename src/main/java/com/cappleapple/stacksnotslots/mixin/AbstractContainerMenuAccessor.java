package com.cappleapple.stacksnotslots.mixin;

import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(AbstractContainerMenu.class)
public interface AbstractContainerMenuAccessor {
    @Invoker("moveItemStackTo")
    boolean sns$moveItemStackTo(ItemStack stack, int startIndex, int endIndex, boolean reverseDirection);
}
