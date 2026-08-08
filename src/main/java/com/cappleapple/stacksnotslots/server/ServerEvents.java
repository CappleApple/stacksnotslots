package com.cappleapple.stacksnotslots.server;

import com.cappleapple.stacksnotslots.StacksNotSlots;
import com.cappleapple.stacksnotslots.attribute.ModAttributes;
import com.cappleapple.stacksnotslots.config.CommonConfig;
import com.cappleapple.stacksnotslots.category.CategoryPresetManager;
import com.cappleapple.stacksnotslots.data.ModAttachments;
import com.cappleapple.stacksnotslots.data.PlayerInventoryData;
import com.cappleapple.stacksnotslots.network.ModNetwork;
import com.cappleapple.stacksnotslots.pickup.PickupFeedback;
import com.cappleapple.stacksnotslots.command.ModCommands;
import java.util.ArrayList;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

public final class ServerEvents {
    private ServerEvents() {}

    @SubscribeEvent
    public static void playerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        PlayerInventoryData data = player.getData(ModAttachments.PLAYER_DATA);
        initializeCapacityBase(player, data);
        migrateVanillaInventory(player, data);
        CategoryPresetManager.initialize(data.categories());
        ModNetwork.sendInitial(player);
    }

    @SubscribeEvent
    public static void playerTick(PlayerTickEvent.Post event) {
        if (!event.getEntity().level().isClientSide) {
            event.getEntity().getData(ModAttachments.PLAYER_DATA).inventory().reconcileExternalMutations();
        }
    }

    @SubscribeEvent
    public static void serverTick(ServerTickEvent.Post event) {
        ModNetwork.flush(event.getServer());
    }

    @SubscribeEvent
    public static void serverStarted(ServerStartedEvent event) {
        CategoryPresetManager.reload();
    }

    @SubscribeEvent
    public static void registerCommands(RegisterCommandsEvent event) {
        ModCommands.register(event.getDispatcher());
    }

    @SubscribeEvent
    public static void playerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        ModNetwork.forget(event.getEntity().getUUID());
        PickupFeedback.forget(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void clonePlayer(PlayerEvent.Clone event) {
        PlayerInventoryData original = event.getOriginal().getData(ModAttachments.PLAYER_DATA);
        PlayerInventoryData replacement = event.getEntity().getData(ModAttachments.PLAYER_DATA);
        replacement.deserializeNBT(event.getEntity().registryAccess(), original.serializeNBT(event.getOriginal().registryAccess()));
    }

    private static void initializeCapacityBase(ServerPlayer player, PlayerInventoryData data) {
        if (data.initializedCapacityBase()) return;
        AttributeInstance attribute = player.getAttribute(ModAttributes.INVENTORY_CAPACITY);
        if (attribute != null) attribute.setBaseValue(CommonConfig.BASE_CAPACITY.getAsInt());
        data.setInitializedCapacityBase();
    }

    private static void migrateVanillaInventory(ServerPlayer player, PlayerInventoryData data) {
        if (data.migratedVanillaInventory()) return;
        ArrayList<ItemStack> vanillaStacks = new ArrayList<>();
        for (ItemStack stack : player.getInventory().items) if (!stack.isEmpty()) vanillaStacks.add(stack.copy());
        data.inventory().importUnbounded(vanillaStacks);
        player.getInventory().items.clear();
        data.setMigratedVanillaInventory();
        StacksNotSlots.LOGGER.info("Migrated {} vanilla inventory stacks for {}", vanillaStacks.size(), player.getGameProfile().getName());
    }
}
