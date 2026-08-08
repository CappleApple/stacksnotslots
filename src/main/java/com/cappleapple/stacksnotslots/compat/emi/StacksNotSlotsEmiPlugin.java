package com.cappleapple.stacksnotslots.compat.emi;

import com.cappleapple.stacksnotslots.client.screen.CapacityInventoryScreen;
import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.widget.Bounds;
import net.minecraft.client.renderer.Rect2i;

/** Reserves the expanded browser drawer so EMI lays its ingredient list around it. */
@EmiEntrypoint
public final class StacksNotSlotsEmiPlugin implements EmiPlugin {
    @Override
    public void register(EmiRegistry registry) {
        registry.addExclusionArea(CapacityInventoryScreen.class, (screen, consumer) -> {
            Rect2i area = screen.drawerBounds();
            consumer.accept(new Bounds(area.getX(), area.getY(), area.getWidth(), area.getHeight()));
        });
    }
}
