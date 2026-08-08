package com.cappleapple.stacksnotslots.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class CommonConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.IntValue BASE_CAPACITY;
    public static final ModConfigSpec.BooleanValue CATEGORY_LIMITS_WORLD_PICKUP;
    public static final ModConfigSpec.BooleanValue CATEGORY_LIMITS_MANUAL_TRANSFERS;
    public static final ModConfigSpec.BooleanValue ALLOW_PARTIAL_PICKUP;
    public static final ModConfigSpec.BooleanValue OVER_CAPACITY_BLOCKS_PICKUP;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.push("inventory");
        BASE_CAPACITY = builder.comment("Base value applied to the inventory_capacity attribute for a new player.")
                .defineInRange("baseInventoryCapacity", 1728, 0, Integer.MAX_VALUE);
        OVER_CAPACITY_BLOCKS_PICKUP = builder.define("overCapacityBlocksPickup", true);
        ALLOW_PARTIAL_PICKUP = builder.define("allowPartialPickup", true);
        builder.pop().push("categories");
        CATEGORY_LIMITS_WORLD_PICKUP = builder.define("categoryLimitsAffectWorldPickup", true);
        CATEGORY_LIMITS_MANUAL_TRANSFERS = builder.define("categoryLimitsAffectManualTransfers", false);
        builder.pop();
        SPEC = builder.build();
    }

    private CommonConfig() {}
}
