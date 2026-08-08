package com.cappleapple.stacksnotslots.client;

import com.cappleapple.stacksnotslots.inventory.ContainerTransfers;
import com.cappleapple.stacksnotslots.network.BulkTransferPayload;
import java.util.List;
import net.minecraft.world.item.ItemStack;

/** Side-neutral storage so common packet registration never links client rendering classes on a server. */
public final class ClientTransientState {
    private static volatile CycleOverlay cycleOverlay;
    private static volatile TransferOverlay transferOverlay;
    private ClientTransientState() {}

    public static void showCycleOverlay(String bindingName, ItemStack selected) {
        cycleOverlay = new CycleOverlay(bindingName, selected.copy(), System.nanoTime() + 2_000_000_000L);
    }

    public static CycleOverlay cycleOverlay() {
        CycleOverlay current = cycleOverlay;
        if (current != null && current.expiresAtNanos() < System.nanoTime()) cycleOverlay = null;
        return cycleOverlay;
    }

    public static void showTransferOverlay(BulkTransferPayload.Direction direction,
                                           List<ContainerTransfers.TransferredStack> stacks, double seconds) {
        transferOverlay = new TransferOverlay(direction, List.copyOf(stacks),
                System.nanoTime() + (long)(seconds * 1_000_000_000L));
    }

    public static TransferOverlay transferOverlay() {
        TransferOverlay current = transferOverlay;
        if (current != null && current.expiresAtNanos() < System.nanoTime()) transferOverlay = null;
        return transferOverlay;
    }

    public record CycleOverlay(String bindingName, ItemStack selected, long expiresAtNanos) {}
    public record TransferOverlay(BulkTransferPayload.Direction direction,
                                  List<ContainerTransfers.TransferredStack> stacks, long expiresAtNanos) {}
}
