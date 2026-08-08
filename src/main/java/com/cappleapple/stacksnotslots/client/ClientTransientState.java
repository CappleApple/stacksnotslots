package com.cappleapple.stacksnotslots.client;

import net.minecraft.world.item.ItemStack;

/** Side-neutral storage so common packet registration never links client rendering classes on a server. */
public final class ClientTransientState {
    private static volatile CycleOverlay cycleOverlay;
    private ClientTransientState() {}

    public static void showCycleOverlay(String bindingName, ItemStack selected) {
        cycleOverlay = new CycleOverlay(bindingName, selected.copy(), System.nanoTime() + 2_000_000_000L);
    }

    public static CycleOverlay cycleOverlay() {
        CycleOverlay current = cycleOverlay;
        if (current != null && current.expiresAtNanos() < System.nanoTime()) cycleOverlay = null;
        return cycleOverlay;
    }

    public record CycleOverlay(String bindingName, ItemStack selected, long expiresAtNanos) {}
}
