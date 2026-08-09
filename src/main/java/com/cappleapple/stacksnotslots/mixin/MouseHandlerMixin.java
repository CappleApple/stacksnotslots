package com.cappleapple.stacksnotslots.mixin;

import com.cappleapple.stacksnotslots.client.ContainerInventoryOverlay;
import com.cappleapple.stacksnotslots.client.ContainerInteractionTrace;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Gives the visibly topmost browser first refusal before JEI, EMI, or screen widgets see mouse input. */
@Mixin(value = MouseHandler.class, priority = 2000)
public abstract class MouseHandlerMixin {
    @Inject(method = "onPress", at = @At("HEAD"), cancellable = true)
    private void sns$browserMouseButton(long windowPointer, int button, int action, int modifiers, CallbackInfo callback) {
        Minecraft minecraft = Minecraft.getInstance();
        if (windowPointer != minecraft.getWindow().getWindow() || minecraft.getOverlay() != null) return;
        boolean handled = ContainerInventoryOverlay.mouseButton(button, action);
        ContainerInteractionTrace.rawMouse(button, action, modifiers, handled);
        if (handled) callback.cancel();
    }

    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void sns$browserMouseScroll(long windowPointer, double deltaX, double deltaY, CallbackInfo callback) {
        Minecraft minecraft = Minecraft.getInstance();
        if (windowPointer != minecraft.getWindow().getWindow() || minecraft.getOverlay() != null) return;
        if (ContainerInventoryOverlay.mouseScrolled(deltaY)) callback.cancel();
    }
}
