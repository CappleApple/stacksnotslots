package com.cappleapple.stacksnotslots.client;

import com.cappleapple.stacksnotslots.api.LogicalInventoryEntry;
import com.cappleapple.stacksnotslots.config.ClientConfig;
import com.cappleapple.stacksnotslots.inventory.CapacityCosts;
import java.util.Locale;

public final class InventoryCountFormatter {
    private InventoryCountFormatter() {}

    public static String item(LogicalInventoryEntry entry, long capacity) {
        return item(entry, capacity, ClientConfig.ITEM_COUNT_MODE.get());
    }

    static String item(LogicalInventoryEntry entry, long capacity, ClientConfig.ItemCountMode mode) {
        long quantity = entry.quantity();
        int stackSize = Math.max(1, entry.representative().getMaxStackSize());
        return switch (mode) {
            case EXACT -> Long.toString(quantity);
            case COMPACT -> compact(quantity);
            case STACKS -> decimal(quantity / (double)stackSize) + "S";
            case STACKS_REMAINDER -> quantity / stackSize + "s+" + quantity % stackSize;
            case PERCENTAGE -> percentage(CapacityCosts.cost(entry.representative(), quantity), capacity);
        };
    }

    public static String overall(long used, long capacity) {
        return overall(used, capacity, ClientConfig.OVERALL_COUNT_MODE.get());
    }

    static String overall(long used, long capacity, ClientConfig.OverallCountMode mode) {
        return switch (mode) {
            case EXACT -> used + " / " + capacity;
            case COMPACT -> compact(used) + " / " + compact(capacity);
            case STACKS -> decimal(used / 64.0) + "S / " + decimal(capacity / 64.0) + "S";
            case PERCENTAGE -> percentage(used, capacity);
        };
    }

    public static String compact(long value) {
        long absolute = value == Long.MIN_VALUE ? Long.MAX_VALUE : Math.abs(value);
        if (absolute < 1_000) return Long.toString(value);
        String[] suffixes = {"k", "m", "b", "t", "q"};
        double scaled = value;
        int suffix = -1;
        do { scaled /= 1_000.0; suffix++; } while (Math.abs(scaled) >= 1_000.0 && suffix + 1 < suffixes.length);
        return decimal(scaled) + suffixes[suffix];
    }

    private static String percentage(long amount, long capacity) {
        if (capacity <= 0) return amount <= 0 ? "0%" : "100%+";
        return decimal(amount * 100.0 / capacity) + "%";
    }

    private static String decimal(double value) {
        if (Math.abs(value - Math.rint(value)) < 0.05) return Long.toString(Math.round(value));
        return String.format(Locale.ROOT, "%.1f", value);
    }
}
