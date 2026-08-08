package com.cappleapple.stacksnotslots.pickup;

import com.cappleapple.stacksnotslots.api.InsertionResult;
import com.cappleapple.stacksnotslots.inventory.DynamicCapacityInventory;
import com.cappleapple.stacksnotslots.network.PickupFeedbackPayload;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

public final class PickupFeedback {
    private static final Map<UUID, Long> LAST_NOTIFICATION = new ConcurrentHashMap<>();
    private PickupFeedback() {}

    public static void notifyRejected(ServerPlayer player, InsertionResult result, DynamicCapacityInventory inventory) {
        if (result.acceptedAll() || result.rejection() == com.cappleapple.stacksnotslots.api.InsertionRejection.NONE) return;
        long now = System.currentTimeMillis();
        Long previous = LAST_NOTIFICATION.put(player.getUUID(), now);
        if (previous != null && now - previous < 1_000) return;
        PacketDistributor.sendToPlayer(player, new PickupFeedbackPayload(result.rejection(), inventory.usedCapacity(), inventory.capacity()));
    }

    public static void forget(UUID playerId) {
        LAST_NOTIFICATION.remove(playerId);
    }
}
