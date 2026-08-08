package com.cappleapple.stacksnotslots.compat.jei;

import com.cappleapple.stacksnotslots.StacksNotSlots;
import com.cappleapple.stacksnotslots.client.screen.CapacityInventoryScreen;
import java.util.List;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.gui.handlers.IGuiContainerHandler;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.resources.ResourceLocation;

/** Reserves the expanded browser drawer so JEI lays its ingredient list around it. */
@JeiPlugin
public final class StacksNotSlotsJeiPlugin implements IModPlugin {
    private static final ResourceLocation ID = StacksNotSlots.id("jei_integration");

    @Override
    public ResourceLocation getPluginUid() {
        return ID;
    }

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        registration.addGuiContainerHandler(CapacityInventoryScreen.class, new IGuiContainerHandler<>() {
            @Override
            public List<Rect2i> getGuiExtraAreas(CapacityInventoryScreen screen) {
                return List.of(screen.drawerBounds());
            }
        });
    }
}
