package com.cappleapple.stacksnotslots.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class ClientConfig {
    public enum CapacityDisplayMode { CAPACITY, STACK_EQUIVALENTS, BOTH }
    public enum PickupNotification { NONE, HUD, ACTION_BAR, SOUND, HUD_AND_SOUND }
    public enum BrowserViewMode { LIST, GRID }
    public enum ItemCountMode { EXACT, COMPACT, STACKS, STACKS_REMAINDER, PERCENTAGE }
    public enum OverallCountMode { EXACT, COMPACT, STACKS, PERCENTAGE }

    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.EnumValue<CapacityDisplayMode> CAPACITY_DISPLAY_MODE;
    public static final ModConfigSpec.EnumValue<PickupNotification> PICKUP_NOTIFICATION;
    public static final ModConfigSpec.BooleanValue TOOLTIP_INDEXING;
    public static final ModConfigSpec.BooleanValue HOTBAR_CYCLE_OVERLAY;
    public static final ModConfigSpec.IntValue CATEGORY_SELECTOR_X;
    public static final ModConfigSpec.IntValue CATEGORY_SELECTOR_Y;
    public static final ModConfigSpec.BooleanValue BROWSER_DISPLACES_CONTAINER;
    public static final ModConfigSpec.EnumValue<BrowserViewMode> BROWSER_VIEW_MODE;
    public static final ModConfigSpec.IntValue BROWSER_GRID_COLUMNS;
    public static final ModConfigSpec.IntValue BROWSER_GRID_ROWS;
    public static final ModConfigSpec.EnumValue<ItemCountMode> ITEM_COUNT_MODE;
    public static final ModConfigSpec.EnumValue<OverallCountMode> OVERALL_COUNT_MODE;
    public static final ModConfigSpec.ConfigValue<String> MANAGE_TABS_ICON;
    public static final ModConfigSpec.ConfigValue<String> SETTINGS_ICON;
    public static final ModConfigSpec.ConfigValue<String> VIEW_MODE_ICON;
    public static final ModConfigSpec.IntValue BROWSER_HANDLE_X;
    public static final ModConfigSpec.IntValue BROWSER_HANDLE_Y;
    public static final ModConfigSpec.BooleanValue BROWSER_HANDLE_VISIBLE;

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
        BROWSER_DISPLACES_CONTAINER = builder.define("browserDisplacesContainer", false);
        BROWSER_VIEW_MODE = builder.defineEnum("browserViewMode", BrowserViewMode.GRID);
        BROWSER_GRID_COLUMNS = builder.defineInRange("browserGridColumns", 4, 1, 16);
        BROWSER_GRID_ROWS = builder.defineInRange("browserGridRows", 6, 1, 20);
        ITEM_COUNT_MODE = builder.defineEnum("browserItemCountMode", ItemCountMode.COMPACT);
        OVERALL_COUNT_MODE = builder.defineEnum("browserOverallCountMode", OverallCountMode.STACKS);
        MANAGE_TABS_ICON = builder.define("manageTabsIcon", "minecraft:name_tag");
        SETTINGS_ICON = builder.define("settingsIcon", "minecraft:redstone");
        VIEW_MODE_ICON = builder.define("viewModeIcon", "minecraft:spyglass");
        BROWSER_HANDLE_X = builder.defineInRange("browserHandleX", -1, -1, 16384);
        BROWSER_HANDLE_Y = builder.defineInRange("browserHandleY", -1, -1, 16384);
        BROWSER_HANDLE_VISIBLE = builder.define("browserHandleVisible", true);
        SPEC = builder.build();
    }

    private ClientConfig() {}
}
