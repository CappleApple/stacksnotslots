package com.cappleapple.stacksnotslots.compat.emi;

import com.cappleapple.stacksnotslots.client.ContainerInventoryOverlay;
import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.widget.Bounds;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.Rect2i;

/** Reserves the floating browser on every container screen so EMI lays its ingredient list around it. */
@EmiEntrypoint
public final class StacksNotSlotsEmiPlugin implements EmiPlugin {
    @Override
    public void register(EmiRegistry registry) {
        // Use EMI's global provider rather than attaching a competing handler to every subclass.
        registry.addGenericExclusionArea((screen, consumer) -> {
            if (!(screen instanceof AbstractContainerScreen<?> containerScreen)) return;
            for (Rect2i area : ContainerInventoryOverlay.currentAreas(containerScreen)) {
                consumer.accept(new Bounds(area.getX(), area.getY(), area.getWidth(), area.getHeight()));
            }
        });
    }
}
