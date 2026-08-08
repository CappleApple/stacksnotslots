package com.cappleapple.stacksnotslots.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class ClientConfig {
    public enum CapacityDisplayMode { CAPACITY, STACK_EQUIVALENTS, BOTH }
    public enum PickupNotification { NONE, HUD, ACTION_BAR, SOUND, HUD_AND_SOUND }

    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.EnumValue<CapacityDisplayMode> CAPACITY_DISPLAY_MODE;
    public static final ModConfigSpec.EnumValue<PickupNotification> PICKUP_NOTIFICATION;
    public static final ModConfigSpec.BooleanValue TOOLTIP_INDEXING;
    public static final ModConfigSpec.BooleanValue HOTBAR_CYCLE_OVERLAY;
    public static final ModConfigSpec.IntValue CATEGORY_SELECTOR_X;
    public static final ModConfigSpec.IntValue CATEGORY_SELECTOR_Y;

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
        SPEC = builder.build();
    }

    private ClientConfig() {}
}
