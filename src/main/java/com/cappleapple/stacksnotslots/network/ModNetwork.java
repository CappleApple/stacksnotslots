package com.cappleapple.stacksnotslots.network;

import com.cappleapple.stacksnotslots.data.ModAttachments;
import com.cappleapple.stacksnotslots.data.PlayerInventoryData;
import com.cappleapple.stacksnotslots.inventory.DynamicCapacityInventory;
import com.cappleapple.stacksnotslots.client.ClientTransientState;
import com.cappleapple.stacksnotslots.category.CategoryDefinition;
import com.cappleapple.stacksnotslots.category.CategoryPresetManager;
import com.cappleapple.stacksnotslots.category.PlayerCategoryData;
import com.cappleapple.stacksnotslots.api.ExtractionResult;
import com.cappleapple.stacksnotslots.hotbar.BindingType;
import com.cappleapple.stacksnotslots.hotbar.HotbarBinding;
import com.cappleapple.stacksnotslots.config.ClientConfig;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class ModNetwork {
    public static final int SNAPSHOT_CHUNK_SIZE = 256;
    /** The largest chunk count representable by an int-sized backing collection. */
    public static final int MAX_SNAPSHOT_CHUNKS = Math.ceilDiv(Integer.MAX_VALUE, SNAPSHOT_CHUNK_SIZE);
    public static final int MAX_DELTA_CHANGES = 4096;
    private static final int MAX_PLAYER_CATEGORIES = 256;
    private static final int MAX_CATEGORY_RULES_PER_SIDE = 128;
    private static final int MAX_CATEGORY_NAME_LENGTH = 64;
    private static final int MAX_CLIENT_ACTIONS_PER_SECOND = 80;
    private static final Set<UUID> DIRTY_PLAYERS = ConcurrentHashMap.newKeySet();
    private static final Map<UUID, SentState> SENT_STATES = new ConcurrentHashMap<>();
    private static final Map<UUID, SnapshotAssembly> CLIENT_SNAPSHOTS = new HashMap<>();
    private static final Map<UUID, ActionRate> ACTION_RATES = new ConcurrentHashMap<>();

    private ModNetwork() {}

    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("2");
        registrar.playToClient(InventorySnapshotPayload.TYPE, InventorySnapshotPayload.STREAM_CODEC, ModNetwork::receiveSnapshot);
        registrar.playToClient(InventoryDeltaPayload.TYPE, InventoryDeltaPayload.STREAM_CODEC, ModNetwork::receiveDelta);
        registrar.playToClient(PlayerMetadataPayload.TYPE, PlayerMetadataPayload.STREAM_CODEC, ModNetwork::receiveMetadata);
        registrar.playToServer(RequestFullSyncPayload.TYPE, RequestFullSyncPayload.STREAM_CODEC, ModNetwork::requestFullSync);
        registrar.playToServer(InventoryActionPayload.TYPE, InventoryActionPayload.STREAM_CODEC, ModNetwork::inventoryAction);
        registrar.playToServer(HotbarCyclePayload.TYPE, HotbarCyclePayload.STREAM_CODEC, ModNetwork::cycleHotbar);
        registrar.playToClient(HotbarCycleResultPayload.TYPE, HotbarCycleResultPayload.STREAM_CODEC, ModNetwork::cycleHotbarResult);
        registrar.playToServer(CategoryEditPayload.TYPE, CategoryEditPayload.STREAM_CODEC, ModNetwork::editCategory);
        registrar.playToServer(HotbarBindPayload.TYPE, HotbarBindPayload.STREAM_CODEC, ModNetwork::bindHotbar);
        registrar.playToServer(InventoryViewPreferencesPayload.TYPE, InventoryViewPreferencesPayload.STREAM_CODEC, ModNetwork::updateViewPreferences);
        registrar.playToClient(PickupFeedbackPayload.TYPE, PickupFeedbackPayload.STREAM_CODEC, ModNetwork::pickupFeedback);
    }

    public static void queueInventorySync(Player player) {
        if (player instanceof ServerPlayer) DIRTY_PLAYERS.add(player.getUUID());
    }

    public static void sendInitial(ServerPlayer player) {
        sendSnapshot(player);
        sendMetadata(player);
    }

    public static void sendMetadata(ServerPlayer player) {
        PlayerInventoryData data = player.getData(ModAttachments.PLAYER_DATA);
        PacketDistributor.sendToPlayer(player, new PlayerMetadataPayload(data.saveMetadata(player.registryAccess())));
    }

    public static void flush(MinecraftServer server) {
        if (DIRTY_PLAYERS.isEmpty()) return;
        List<UUID> queued = List.copyOf(DIRTY_PLAYERS);
        DIRTY_PLAYERS.removeAll(queued);
        for (UUID id : queued) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player != null) sendDeltaOrSnapshot(player);
        }
    }

    public static void forget(UUID playerId) {
        DIRTY_PLAYERS.remove(playerId);
        SENT_STATES.remove(playerId);
        ACTION_RATES.remove(playerId);
    }

    private static void sendDeltaOrSnapshot(ServerPlayer player) {
        DynamicCapacityInventory inventory = player.getData(ModAttachments.PLAYER_DATA).inventory();
        List<ItemStack> current = inventory.backingStacks();
        SentState previous = SENT_STATES.get(player.getUUID());
        if (previous == null) { sendSnapshot(player); return; }
        ArrayList<InventoryDeltaPayload.SlotChange> changes = new ArrayList<>();
        int compared = Math.min(previous.stacks.size(), current.size());
        for (int i = 0; i < compared; i++) {
            if (!ItemStack.matches(previous.stacks.get(i), current.get(i))) changes.add(new InventoryDeltaPayload.SlotChange(i, current.get(i)));
        }
        for (int i = compared; i < current.size(); i++) changes.add(new InventoryDeltaPayload.SlotChange(i, current.get(i)));
        if (changes.size() > MAX_DELTA_CHANGES || changes.size() > Math.max(64, current.size() / 2)) {
            sendSnapshot(player);
            return;
        }
        PacketDistributor.sendToPlayer(player, new InventoryDeltaPayload(previous.revision, inventory.revision(), current.size(), changes));
        SENT_STATES.put(player.getUUID(), new SentState(inventory.revision(), current));
    }

    private static void sendSnapshot(ServerPlayer player) {
        DynamicCapacityInventory inventory = player.getData(ModAttachments.PLAYER_DATA).inventory();
        List<ItemStack> stacks = inventory.backingStacks();
        int chunkCount = Math.max(1, Math.ceilDiv(stacks.size(), SNAPSHOT_CHUNK_SIZE));
        UUID snapshotId = UUID.randomUUID();
        for (int chunk = 0; chunk < chunkCount; chunk++) {
            int from = chunk * SNAPSHOT_CHUNK_SIZE;
            int to = (int)Math.min((long)stacks.size(), (long)from + SNAPSHOT_CHUNK_SIZE);
            PacketDistributor.sendToPlayer(player, new InventorySnapshotPayload(snapshotId, inventory.revision(), chunk, chunkCount, stacks.subList(from, to)));
        }
        SENT_STATES.put(player.getUUID(), new SentState(inventory.revision(), stacks));
    }

    private static void receiveSnapshot(InventorySnapshotPayload payload, IPayloadContext context) {
        long expiry = System.nanoTime() - java.util.concurrent.TimeUnit.SECONDS.toNanos(30);
        CLIENT_SNAPSHOTS.values().removeIf(assembly -> assembly.createdAtNanos < expiry);
        SnapshotAssembly assembly = CLIENT_SNAPSHOTS.computeIfAbsent(payload.snapshotId(), ignored -> new SnapshotAssembly(payload.revision(), payload.chunkCount()));
        if (assembly.revision != payload.revision() || assembly.chunkCount != payload.chunkCount()) {
            CLIENT_SNAPSHOTS.remove(payload.snapshotId());
            return;
        }
        assembly.chunks.put(payload.chunkIndex(), payload.stacks());
        if (assembly.chunks.size() == assembly.chunkCount) {
            ArrayList<ItemStack> stacks = new ArrayList<>();
            for (int i = 0; i < assembly.chunkCount; i++) stacks.addAll(assembly.chunks.get(i));
            PlayerInventoryData data = context.player().getData(ModAttachments.PLAYER_DATA);
            data.inventory().loadNetworkSnapshot(stacks, assembly.revision);
            data.setMigratedVanillaInventory();
            CLIENT_SNAPSHOTS.remove(payload.snapshotId());
        }
    }

    private static void receiveDelta(InventoryDeltaPayload payload, IPayloadContext context) {
        Map<Integer, ItemStack> changes = new LinkedHashMap<>();
        for (InventoryDeltaPayload.SlotChange change : payload.changes()) changes.put(change.index(), change.stack());
        boolean applied = context.player().getData(ModAttachments.PLAYER_DATA).inventory()
                .applyNetworkDelta(payload.baseRevision(), payload.revision(), payload.resultingSize(), changes);
        if (!applied) PacketDistributor.sendToServer(new RequestFullSyncPayload());
    }

    private static void receiveMetadata(PlayerMetadataPayload payload, IPayloadContext context) {
        context.player().getData(ModAttachments.PLAYER_DATA).loadMetadata(context.player().registryAccess(), payload.data());
    }

    private static void requestFullSync(RequestFullSyncPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) sendSnapshot(player);
    }

    private static void inventoryAction(InventoryActionPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player) || !allowAction(player)) return;
        DynamicCapacityInventory inventory = player.getData(ModAttachments.PLAYER_DATA).inventory();
        ItemStack prototype = payload.prototype();
        int amount;
        switch (payload.action()) {
            case DROP_ONE -> amount = 1;
            case DROP_STACK -> amount = prototype.getMaxStackSize();
            case TAKE_STACK, TAKE_HALF -> {
                ItemStack carried = player.containerMenu.getCarried();
                if (!carried.isEmpty() && !ItemStack.isSameItemSameComponents(carried, prototype)) return;
                int space = carried.isEmpty() ? prototype.getMaxStackSize() : carried.getMaxStackSize() - carried.getCount();
                if (space <= 0) return;
                int available = inventory.extract(prototype, space, true).extractedAmount();
                amount = payload.action() == InventoryActionPayload.Action.TAKE_HALF ? Math.max(1, Math.ceilDiv(available, 2)) : available;
                ExtractionResult extraction = inventory.extract(prototype, amount, false);
                if (extraction.extractedAmount() == 0) return;
                if (carried.isEmpty()) player.containerMenu.setCarried(prototype.copyWithCount(extraction.extractedAmount()));
                else carried.grow(extraction.extractedAmount());
                player.containerMenu.broadcastChanges();
                return;
            }
            default -> throw new IllegalStateException("Unhandled inventory action");
        }
        ExtractionResult extraction = inventory.extract(prototype, amount, false);
        extraction.extractedStacks().forEach(stack -> player.drop(stack, false));
    }

    private static void cycleHotbar(HotbarCyclePayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player) || !allowAction(player)) return;
        if (payload.slot() < 0 || payload.slot() >= 9 || Math.abs(payload.direction()) != 1) return;
        PlayerInventoryData data = player.getData(ModAttachments.PLAYER_DATA);
        ItemStack selected = data.hotbar().cycle(payload.slot(), payload.direction(), data.inventory(), data.categories());
        CategoryDefinition category = data.hotbar().get(payload.slot()).target() == null ? null : data.categories().find(data.hotbar().get(payload.slot()).target());
        sendMetadata(player);
        PacketDistributor.sendToPlayer(player, new HotbarCycleResultPayload(category == null ? "Hotbar" : category.displayName(), selected));
        player.inventoryMenu.broadcastChanges();
    }

    private static void cycleHotbarResult(HotbarCycleResultPayload payload, IPayloadContext context) {
        ClientTransientState.showCycleOverlay(payload.bindingName(), payload.selected());
    }

    private static boolean allowAction(ServerPlayer player) {
        long second = System.currentTimeMillis() / 1000L;
        ActionRate rate = ACTION_RATES.computeIfAbsent(player.getUUID(), ignored -> new ActionRate());
        if (rate.second != second) { rate.second = second; rate.count = 0; }
        return ++rate.count <= MAX_CLIENT_ACTIONS_PER_SECOND;
    }

    private static void editCategory(CategoryEditPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player) || !allowAction(player)) return;
        PlayerCategoryData categories = player.getData(ModAttachments.PLAYER_DATA).categories();
        switch (payload.operation()) {
            case RESET -> CategoryPresetManager.reset(categories);
            case DELETE -> {
                String encodedId = payload.category().getString("Id");
                var id = net.minecraft.resources.ResourceLocation.tryParse(encodedId);
                if (id != null) categories.remove(id);
            }
            case UPSERT -> {
                CategoryDefinition definition = PlayerCategoryData.loadCategory(payload.category());
                if (definition == null || !validCategory(definition, categories)) return;
                categories.upsert(definition);
            }
        }
        sendMetadata(player);
    }

    private static boolean validCategory(CategoryDefinition category, PlayerCategoryData existing) {
        boolean mayAdd = existing.find(category.id()) != null || existing.categories().size() < MAX_PLAYER_CATEGORIES;
        return mayAdd
                && !category.displayName().isBlank() && category.displayName().length() <= MAX_CATEGORY_NAME_LENGTH
                && category.includes().size() <= MAX_CATEGORY_RULES_PER_SIDE && category.excludes().size() <= MAX_CATEGORY_RULES_PER_SIDE
                && category.pickupLimit() >= -1 && category.pickupLimit() <= Integer.MAX_VALUE;
    }

    private static void bindHotbar(HotbarBindPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player) || !allowAction(player) || payload.slot() < 0 || payload.slot() >= 9) return;
        PlayerInventoryData data = player.getData(ModAttachments.PLAYER_DATA);
        if (payload.bindingType() == BindingType.CATEGORY && data.categories().find(payload.target()) == null) return;
        if (payload.bindingType() == BindingType.ITEM && BuiltInRegistries.ITEM.getOptional(payload.target()).isEmpty()) return;
        if (payload.bindingType() != BindingType.EMPTY && payload.bindingType() != BindingType.ITEM && payload.bindingType() != BindingType.CATEGORY) return;
        data.hotbar().set(payload.slot(), payload.bindingType() == BindingType.EMPTY
                ? HotbarBinding.empty()
                : new HotbarBinding(payload.bindingType(), payload.target(), null));
        sendMetadata(player);
        player.inventoryMenu.broadcastChanges();
    }

    private static void updateViewPreferences(InventoryViewPreferencesPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player) || !allowAction(player)) return;
        PlayerInventoryData data = player.getData(ModAttachments.PLAYER_DATA);
        if (payload.selectedCategory() != null && data.categories().find(payload.selectedCategory()) == null) return;
        data.setInventorySortPreference(payload.sortMode());
        data.setSelectedCategoryPreference(payload.selectedCategory());
        sendMetadata(player);
    }

    private static void pickupFeedback(PickupFeedbackPayload payload, IPayloadContext context) {
        ClientConfig.PickupNotification mode = ClientConfig.PICKUP_NOTIFICATION.get();
        if (mode == ClientConfig.PickupNotification.NONE) return;
        Component message = payload.reason() == com.cappleapple.stacksnotslots.api.InsertionRejection.CATEGORY_LIMIT
                ? Component.translatable("message.stacksnotslots.category_limit")
                : Component.translatable("message.stacksnotslots.inventory_full", payload.usedCapacity(), payload.capacity());
        if (mode == ClientConfig.PickupNotification.HUD || mode == ClientConfig.PickupNotification.ACTION_BAR || mode == ClientConfig.PickupNotification.HUD_AND_SOUND) {
            context.player().displayClientMessage(message, true);
        }
        if (mode == ClientConfig.PickupNotification.SOUND || mode == ClientConfig.PickupNotification.HUD_AND_SOUND) {
            context.player().playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.35F, 0.7F);
        }
    }

    private record SentState(long revision, List<ItemStack> stacks) {
        private SentState { stacks = stacks.stream().map(ItemStack::copy).toList(); }
    }

    private static final class SnapshotAssembly {
        private final long revision;
        private final int chunkCount;
        private final long createdAtNanos = System.nanoTime();
        private final Map<Integer, List<ItemStack>> chunks = new HashMap<>();
        private SnapshotAssembly(long revision, int chunkCount) { this.revision = revision; this.chunkCount = chunkCount; }
    }

    private static final class ActionRate { private long second; private int count; }
}
