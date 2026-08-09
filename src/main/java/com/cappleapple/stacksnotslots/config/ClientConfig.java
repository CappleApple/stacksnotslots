package com.cappleapple.stacksnotslots.config;

import java.util.List;
import net.neoforged.neoforge.common.ModConfigSpec;

public final class ClientConfig {
    public enum CapacityDisplayMode { CAPACITY, STACK_EQUIVALENTS, BOTH }
    public enum PickupNotification { NONE, HUD, ACTION_BAR, SOUND, HUD_AND_SOUND }
    public enum BrowserViewMode { LIST, GRID }
    public enum ItemCountMode { EXACT, COMPACT, STACKS, STACKS_REMAINDER, PERCENTAGE }
    public enum OverallCountMode { EXACT, COMPACT, STACKS, PERCENTAGE }
    public enum BrowserDockSide { LEFT, RIGHT, TOP, BOTTOM }
    public enum BrowserDefaultPlacement { BOTTOM_RIGHT, RIGHT_CENTER, TOP_RIGHT, BOTTOM_LEFT, LEFT_CENTER, TOP_LEFT }

    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.EnumValue<CapacityDisplayMode> CAPACITY_DISPLAY_MODE;
    public static final ModConfigSpec.EnumValue<PickupNotification> PICKUP_NOTIFICATION;
    public static final ModConfigSpec.BooleanValue TOOLTIP_INDEXING;
    public static final ModConfigSpec.BooleanValue HOTBAR_CYCLE_OVERLAY;
    public static final ModConfigSpec.IntValue CATEGORY_SELECTOR_X;
    public static final ModConfigSpec.IntValue CATEGORY_SELECTOR_Y;
    public static final ModConfigSpec.EnumValue<BrowserViewMode> BROWSER_VIEW_MODE;
    public static final ModConfigSpec.IntValue BROWSER_GRID_COLUMNS;
    public static final ModConfigSpec.IntValue BROWSER_GRID_ROWS;
    public static final ModConfigSpec.EnumValue<ItemCountMode> ITEM_COUNT_MODE;
    public static final ModConfigSpec.EnumValue<OverallCountMode> OVERALL_COUNT_MODE;
    public static final ModConfigSpec.ConfigValue<String> MANAGE_TABS_ICON;
    public static final ModConfigSpec.ConfigValue<String> SETTINGS_ICON;
    public static final ModConfigSpec.ConfigValue<String> BROWSER_HANDLE_ICON;
    public static final ModConfigSpec.BooleanValue BROWSER_HANDLE_ICON_MIGRATED;
    public static final ModConfigSpec.IntValue BROWSER_HANDLE_X;
    public static final ModConfigSpec.IntValue BROWSER_HANDLE_Y;
    public static final ModConfigSpec.BooleanValue BROWSER_HANDLE_VISIBLE;
    public static final ModConfigSpec.EnumValue<BrowserDockSide> BROWSER_DOCK_SIDE;
    public static final ModConfigSpec.EnumValue<BrowserDefaultPlacement> BROWSER_DEFAULT_PLACEMENT;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> BROWSER_SCREEN_STATES;
    public static final ModConfigSpec.BooleanValue AUTO_BROWSER_DOCK_SIDE;
    public static final ModConfigSpec.IntValue AUTO_DOCK_DEAD_ZONE_X;
    public static final ModConfigSpec.IntValue AUTO_DOCK_DEAD_ZONE_Y;
    public static final ModConfigSpec.BooleanValue BULK_TRANSFER_OVERLAY;
    public static final ModConfigSpec.DoubleValue BULK_TRANSFER_OVERLAY_SECONDS;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        CAPACITY_DISPLAY_MODE = builder.defineEnum("capacityDisplayMode", CapacityDisplayMode.BOTH);
        PICKUP_NOTIFICATION = builder.defineEnum("pickupLimitNotification", PickupNotification.HUD);
        TOOLTIP_INDEXING = builder.define("enableSearchTooltipIndexing", true);
        HOTBAR_CYCLE_OVERLAY = builder.define("enableHotbarCycleOverlay", true);
        CATEGORY_SELECTOR_X = builder.comment("Category selector X offset from the vanilla inventory's left edge")
                .defineInRange("categorySelectorX", 134, -256, 512);
        CATEGORY_SELECTOR_Y = builder.comment("Category selector Y offset from the vanilla inventory's top edge")
                .defineInRange("categorySelectorY", 61, -256, 512);
        BROWSER_VIEW_MODE = builder.defineEnum("browserViewMode", BrowserViewMode.GRID);
        BROWSER_GRID_COLUMNS = builder.defineInRange("browserGridColumns", 4, 1, 16);
        BROWSER_GRID_ROWS = builder.defineInRange("browserGridRows", 6, 1, 20);
        ITEM_COUNT_MODE = builder.defineEnum("browserItemCountMode", ItemCountMode.COMPACT);
        OVERALL_COUNT_MODE = builder.defineEnum("browserOverallCountMode", OverallCountMode.STACKS);
        MANAGE_TABS_ICON = builder.define("manageTabsIcon", "minecraft:name_tag");
        SETTINGS_ICON = builder.define("settingsIcon", "minecraft:redstone");
        BROWSER_HANDLE_ICON = builder.define("browserHandleIcon", "stacksnotslots:logo");
        BROWSER_HANDLE_ICON_MIGRATED = builder.comment("Internal one-time migration from the pre-0.6.3 spyglass default")
                .define("browserHandleIconMigrated", false);
        // These four values remain defaults for screen types without a saved state, preserving old configs.
        BROWSER_HANDLE_X = builder.defineInRange("browserHandleX", -1, -1, 16384);
        BROWSER_HANDLE_Y = builder.defineInRange("browserHandleY", -1, -1, 16384);
        BROWSER_HANDLE_VISIBLE = builder.define("browserHandleVisible", true);
        BROWSER_DOCK_SIDE = builder.defineEnum("browserDockSide", BrowserDockSide.RIGHT);
        BROWSER_DEFAULT_PLACEMENT = builder.defineEnum("browserDefaultPlacement", BrowserDefaultPlacement.BOTTOM_RIGHT);
        BROWSER_SCREEN_STATES = builder.comment("Internal per-screen browser position, visibility, open state, and dock side")
                .defineListAllowEmpty("browserScreenStates", List.of(), null, value -> value instanceof String);
        AUTO_BROWSER_DOCK_SIDE = builder.define("autoChooseBrowserSide", true);
        AUTO_DOCK_DEAD_ZONE_X = builder.defineInRange("autoSideDeadZoneX", 48, 0, 4096);
        AUTO_DOCK_DEAD_ZONE_Y = builder.defineInRange("autoSideDeadZoneY", 36, 0, 4096);
        BULK_TRANSFER_OVERLAY = builder.define("showBulkTransferOverlay", true);
        BULK_TRANSFER_OVERLAY_SECONDS = builder.defineInRange("bulkTransferOverlaySeconds", 2.5D, 0.25D, 30.0D);
        SPEC = builder.build();
    }

    private ClientConfig() {}
}
