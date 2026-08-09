package com.cappleapple.stacksnotslots.client;

import com.cappleapple.stacksnotslots.StacksNotSlots;
import com.cappleapple.stacksnotslots.config.ClientConfig;
import com.cappleapple.stacksnotslots.data.PlayerInventoryData;
import com.cappleapple.stacksnotslots.network.PlayerCustomizationPayload;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Durable client-owned state stored beside the config directory, where modpack config refreshes
 * cannot replace player customizations.
 */
public final class ClientSaveState {
    public static final String FILE_NAME = "SNS-SaveState.json";
    private static final int SCHEMA_VERSION = 1;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static SaveData data;
    private static boolean loaded;
    private static UUID activePlayer;
    private static boolean activeProfileApplied;

    private ClientSaveState() {}

    public static synchronized void initialize() {
        if (loaded) return;
        Path path = savePath();
        boolean migrated = Files.notExists(path);
        try {
            data = migrated ? new SaveData() : read(path);
            normalize();
        } catch (Exception exception) {
            StacksNotSlots.LOGGER.error("Could not load {}; using current client defaults", path, exception);
            backupMalformedFile(path);
            data = new SaveData();
            normalize();
            migrated = true;
        }
        loaded = true;
        if (migrated) {
            captureClientSettings();
            importLegacyBrowserStates();
            saveNow();
        } else {
            applyClientSettings();
        }
        StacksNotSlots.LOGGER.info("Loaded client save state from {}", path);
    }

    public static synchronized void beginConnection(Player player) {
        initialize();
        activePlayer = player.getUUID();
        activeProfileApplied = false;
    }

    public static synchronized void endConnection() {
        activePlayer = null;
        activeProfileApplied = false;
    }

    /** Applies the local profile once, then persists every server-validated metadata update. */
    public static synchronized void receiveMetadata(Player player, PlayerInventoryData inventoryData) {
        initialize();
        UUID playerId = player.getUUID();
        if (!playerId.equals(activePlayer)) {
            activePlayer = playerId;
            activeProfileApplied = false;
        }

        PlayerProfile saved = data.players.get(playerId.toString());
        if (!activeProfileApplied && saved != null && saved.customization != null && !saved.customization.isBlank()) {
            try {
                CompoundTag customization = TagParser.parseTag(saved.customization);
                inventoryData.loadCustomization(player.registryAccess(), customization);
                activeProfileApplied = true;
                PacketDistributor.sendToServer(new PlayerCustomizationPayload(customization));
                return;
            } catch (Exception exception) {
                StacksNotSlots.LOGGER.error("Ignoring invalid saved customization for player {}", playerId, exception);
                backupMalformedFile(savePath());
            }
        }

        activeProfileApplied = true;
        savePlayerProfile(playerId, inventoryData.saveCustomization(player.registryAccess()));
    }

    public static synchronized void saveClientSettings() {
        initialize();
        captureClientSettings();
        saveNow();
    }

    static synchronized Optional<BrowserScreenStateStore.State> browserState(String screenType) {
        initialize();
        SavedBrowserState saved = data.browserScreens.get(screenType);
        if (saved == null) return Optional.empty();
        try {
            return Optional.of(new BrowserScreenStateStore.State(screenType, saved.offsetX, saved.offsetY,
                    saved.open, saved.visible, ClientConfig.BrowserDockSide.valueOf(saved.dockSide)));
        } catch (IllegalArgumentException | NullPointerException ignored) {
            return Optional.empty();
        }
    }

    static synchronized void saveBrowserState(BrowserScreenStateStore.State state) {
        initialize();
        data.browserScreens.put(state.screenType(), new SavedBrowserState(
                state.offsetX(), state.offsetY(), state.open(), state.visible(), state.dockSide().name()));
        saveNow();
    }

    public static Path savePath() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve(FILE_NAME);
    }

    private static SaveData read(Path path) throws IOException {
        JsonObject root = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
        int version = root.has("schemaVersion") ? root.get("schemaVersion").getAsInt() : 1;
        if (version != SCHEMA_VERSION) throw new IllegalArgumentException("Unsupported save-state schema " + version);
        SaveData result = GSON.fromJson(root, SaveData.class);
        if (result == null) throw new IllegalArgumentException("Save-state file is empty");
        return result;
    }

    private static void normalize() {
        data.schemaVersion = SCHEMA_VERSION;
        if (data.settings == null) data.settings = new JsonObject();
        if (data.browserScreens == null) data.browserScreens = new LinkedHashMap<>();
        if (data.players == null) data.players = new LinkedHashMap<>();
    }

    private static void savePlayerProfile(UUID playerId, CompoundTag customization) {
        String encoded = customization.toString();
        PlayerProfile existing = data.players.get(playerId.toString());
        if (existing != null && encoded.equals(existing.customization)) return;
        data.players.put(playerId.toString(), new PlayerProfile(encoded));
        saveNow();
    }

    private static void captureClientSettings() {
        JsonObject settings = new JsonObject();
        put(settings, "capacityDisplayMode", ClientConfig.CAPACITY_DISPLAY_MODE.get().name());
        put(settings, "pickupLimitNotification", ClientConfig.PICKUP_NOTIFICATION.get().name());
        put(settings, "enableSearchTooltipIndexing", ClientConfig.TOOLTIP_INDEXING.getAsBoolean());
        put(settings, "enableHotbarCycleOverlay", ClientConfig.HOTBAR_CYCLE_OVERLAY.getAsBoolean());
        put(settings, "categorySelectorX", ClientConfig.CATEGORY_SELECTOR_X.getAsInt());
        put(settings, "categorySelectorY", ClientConfig.CATEGORY_SELECTOR_Y.getAsInt());
        put(settings, "browserViewMode", ClientConfig.BROWSER_VIEW_MODE.get().name());
        put(settings, "browserGridColumns", ClientConfig.BROWSER_GRID_COLUMNS.getAsInt());
        put(settings, "browserGridRows", ClientConfig.BROWSER_GRID_ROWS.getAsInt());
        put(settings, "browserItemCountMode", ClientConfig.ITEM_COUNT_MODE.get().name());
        put(settings, "browserOverallCountMode", ClientConfig.OVERALL_COUNT_MODE.get().name());
        put(settings, "manageTabsIcon", ClientConfig.MANAGE_TABS_ICON.get());
        put(settings, "settingsIcon", ClientConfig.SETTINGS_ICON.get());
        put(settings, "browserHandleIcon", ClientConfig.BROWSER_HANDLE_ICON.get());
        put(settings, "browserHandleIconMigrated", ClientConfig.BROWSER_HANDLE_ICON_MIGRATED.getAsBoolean());
        put(settings, "browserHandleSpyglassRestored", ClientConfig.BROWSER_HANDLE_SPYGLASS_RESTORED.getAsBoolean());
        put(settings, "browserHandleX", ClientConfig.BROWSER_HANDLE_X.getAsInt());
        put(settings, "browserHandleY", ClientConfig.BROWSER_HANDLE_Y.getAsInt());
        put(settings, "browserHandleVisible", ClientConfig.BROWSER_HANDLE_VISIBLE.getAsBoolean());
        put(settings, "browserDockSide", ClientConfig.BROWSER_DOCK_SIDE.get().name());
        put(settings, "browserDefaultPlacement", ClientConfig.BROWSER_DEFAULT_PLACEMENT.get().name());
        put(settings, "autoChooseBrowserSide", ClientConfig.AUTO_BROWSER_DOCK_SIDE.getAsBoolean());
        put(settings, "autoSideDeadZoneX", ClientConfig.AUTO_DOCK_DEAD_ZONE_X.getAsInt());
        put(settings, "autoSideDeadZoneY", ClientConfig.AUTO_DOCK_DEAD_ZONE_Y.getAsInt());
        put(settings, "showBulkTransferOverlay", ClientConfig.BULK_TRANSFER_OVERLAY.getAsBoolean());
        put(settings, "bulkTransferOverlaySeconds", ClientConfig.BULK_TRANSFER_OVERLAY_SECONDS.get());
        data.settings = settings;
    }

    private static void applyClientSettings() {
        JsonObject settings = data.settings;
        setEnum(settings, "capacityDisplayMode", ClientConfig.CapacityDisplayMode.class, ClientConfig.CAPACITY_DISPLAY_MODE::set);
        setEnum(settings, "pickupLimitNotification", ClientConfig.PickupNotification.class, ClientConfig.PICKUP_NOTIFICATION::set);
        setBoolean(settings, "enableSearchTooltipIndexing", ClientConfig.TOOLTIP_INDEXING::set);
        setBoolean(settings, "enableHotbarCycleOverlay", ClientConfig.HOTBAR_CYCLE_OVERLAY::set);
        setInteger(settings, "categorySelectorX", -256, 512, ClientConfig.CATEGORY_SELECTOR_X::set);
        setInteger(settings, "categorySelectorY", -256, 512, ClientConfig.CATEGORY_SELECTOR_Y::set);
        setEnum(settings, "browserViewMode", ClientConfig.BrowserViewMode.class, ClientConfig.BROWSER_VIEW_MODE::set);
        setInteger(settings, "browserGridColumns", 1, 16, ClientConfig.BROWSER_GRID_COLUMNS::set);
        setInteger(settings, "browserGridRows", 1, 20, ClientConfig.BROWSER_GRID_ROWS::set);
        setEnum(settings, "browserItemCountMode", ClientConfig.ItemCountMode.class, ClientConfig.ITEM_COUNT_MODE::set);
        setEnum(settings, "browserOverallCountMode", ClientConfig.OverallCountMode.class, ClientConfig.OVERALL_COUNT_MODE::set);
        setString(settings, "manageTabsIcon", ClientConfig.MANAGE_TABS_ICON::set);
        setString(settings, "settingsIcon", ClientConfig.SETTINGS_ICON::set);
        setString(settings, "browserHandleIcon", ClientConfig.BROWSER_HANDLE_ICON::set);
        setBoolean(settings, "browserHandleIconMigrated", ClientConfig.BROWSER_HANDLE_ICON_MIGRATED::set);
        setBoolean(settings, "browserHandleSpyglassRestored", ClientConfig.BROWSER_HANDLE_SPYGLASS_RESTORED::set);
        setInteger(settings, "browserHandleX", -1, 16384, ClientConfig.BROWSER_HANDLE_X::set);
        setInteger(settings, "browserHandleY", -1, 16384, ClientConfig.BROWSER_HANDLE_Y::set);
        setBoolean(settings, "browserHandleVisible", ClientConfig.BROWSER_HANDLE_VISIBLE::set);
        setEnum(settings, "browserDockSide", ClientConfig.BrowserDockSide.class, ClientConfig.BROWSER_DOCK_SIDE::set);
        setEnum(settings, "browserDefaultPlacement", ClientConfig.BrowserDefaultPlacement.class, ClientConfig.BROWSER_DEFAULT_PLACEMENT::set);
        setBoolean(settings, "autoChooseBrowserSide", ClientConfig.AUTO_BROWSER_DOCK_SIDE::set);
        setInteger(settings, "autoSideDeadZoneX", 0, 4096, ClientConfig.AUTO_DOCK_DEAD_ZONE_X::set);
        setInteger(settings, "autoSideDeadZoneY", 0, 4096, ClientConfig.AUTO_DOCK_DEAD_ZONE_Y::set);
        setBoolean(settings, "showBulkTransferOverlay", ClientConfig.BULK_TRANSFER_OVERLAY::set);
        setDouble(settings, "bulkTransferOverlaySeconds", 0.25D, 30.0D, ClientConfig.BULK_TRANSFER_OVERLAY_SECONDS::set);
    }

    private static void importLegacyBrowserStates() {
        for (String encoded : ClientConfig.BROWSER_SCREEN_STATES.get()) {
            BrowserScreenStateStore.decode(encoded).ifPresent(state -> data.browserScreens.put(state.screenType(),
                    new SavedBrowserState(state.offsetX(), state.offsetY(), state.open(), state.visible(), state.dockSide().name())));
        }
    }

    private static void saveNow() {
        Path target = savePath();
        Path temporary = null;
        try {
            Files.createDirectories(target.getParent());
            temporary = Files.createTempFile(target.getParent(), "SNS-SaveState-", ".tmp");
            Files.writeString(temporary, GSON.toJson(data), StandardCharsets.UTF_8);
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            StacksNotSlots.LOGGER.error("Could not save client state to {}", target, exception);
        } finally {
            if (temporary != null) {
                try { Files.deleteIfExists(temporary); }
                catch (IOException ignored) {}
            }
        }
    }

    private static void backupMalformedFile(Path path) {
        if (Files.notExists(path)) return;
        String timestamp = DateTimeFormatter.ISO_INSTANT.format(Instant.now()).replace(':', '-');
        Path backup = path.resolveSibling("SNS-SaveState.corrupt-" + timestamp + ".json");
        try { Files.copy(path, backup, StandardCopyOption.REPLACE_EXISTING); }
        catch (IOException exception) { StacksNotSlots.LOGGER.error("Could not back up malformed save state {}", path, exception); }
    }

    private static void put(JsonObject object, String key, String value) { object.addProperty(key, value); }
    private static void put(JsonObject object, String key, boolean value) { object.addProperty(key, value); }
    private static void put(JsonObject object, String key, int value) { object.addProperty(key, value); }
    private static void put(JsonObject object, String key, double value) { object.addProperty(key, value); }

    private static void setString(JsonObject object, String key, java.util.function.Consumer<String> setter) {
        JsonElement value = object.get(key);
        if (value != null && value.isJsonPrimitive()) setter.accept(value.getAsString());
    }

    private static void setBoolean(JsonObject object, String key, java.util.function.Consumer<Boolean> setter) {
        JsonElement value = object.get(key);
        if (value == null || !value.isJsonPrimitive()) return;
        try { setter.accept(value.getAsBoolean()); }
        catch (RuntimeException exception) { warnInvalid(key, exception); }
    }

    private static void setInteger(JsonObject object, String key, int minimum, int maximum,
                                   java.util.function.Consumer<Integer> setter) {
        JsonElement value = object.get(key);
        if (value == null || !value.isJsonPrimitive()) return;
        try { setter.accept(Math.max(minimum, Math.min(maximum, value.getAsInt()))); }
        catch (RuntimeException exception) { warnInvalid(key, exception); }
    }

    private static void setDouble(JsonObject object, String key, double minimum, double maximum,
                                  java.util.function.Consumer<Double> setter) {
        JsonElement value = object.get(key);
        if (value == null || !value.isJsonPrimitive()) return;
        try { setter.accept(Math.max(minimum, Math.min(maximum, value.getAsDouble()))); }
        catch (RuntimeException exception) { warnInvalid(key, exception); }
    }

    private static <T extends Enum<T>> void setEnum(JsonObject object, String key, Class<T> type,
                                                     java.util.function.Consumer<T> setter) {
        JsonElement value = object.get(key);
        if (value == null || !value.isJsonPrimitive()) return;
        try { setter.accept(Enum.valueOf(type, value.getAsString())); }
        catch (RuntimeException exception) { warnInvalid(key, exception); }
    }

    private static void warnInvalid(String key, RuntimeException exception) {
        StacksNotSlots.LOGGER.warn("Ignoring invalid {} value in {}", key, FILE_NAME, exception);
    }

    private static final class SaveData {
        private int schemaVersion = SCHEMA_VERSION;
        private JsonObject settings = new JsonObject();
        private Map<String, SavedBrowserState> browserScreens = new LinkedHashMap<>();
        private Map<String, PlayerProfile> players = new LinkedHashMap<>();
    }

    private record SavedBrowserState(int offsetX, int offsetY, boolean open, boolean visible, String dockSide) {}
    private record PlayerProfile(String customization) {}
}
