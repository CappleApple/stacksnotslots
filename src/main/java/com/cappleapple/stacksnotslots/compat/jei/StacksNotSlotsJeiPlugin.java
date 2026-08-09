package com.cappleapple.stacksnotslots.compat.jei;

import com.cappleapple.stacksnotslots.StacksNotSlots;
import com.cappleapple.stacksnotslots.client.ContainerInventoryOverlay;
import java.util.Collection;
import java.util.List;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.gui.handlers.IGlobalGuiHandler;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.resources.ResourceLocation;

/** Reserves the floating browser on every container screen so JEI lays its ingredient list around it. */
@JeiPlugin
public final class StacksNotSlotsJeiPlugin implements IModPlugin {
    private static final ResourceLocation ID = StacksNotSlots.id("jei_integration");

    @Override
    public ResourceLocation getPluginUid() {
        return ID;
    }

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        // A global extra-area provider does not join or replace a concrete screen's own JEI handler.
        // That distinction matters for custom screens such as Sophisticated Core's StorageScreenBase.
        registration.addGlobalGuiHandler(new IGlobalGuiHandler() {
            @Override
            public Collection<Rect2i> getGuiExtraAreas() {
                return Minecraft.getInstance().screen instanceof AbstractContainerScreen<?> screen
                        ? ContainerInventoryOverlay.currentAreas(screen)
                        : List.of();
            }
        });
    }
}
