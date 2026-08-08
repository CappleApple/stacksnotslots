package com.cappleapple.stacksnotslots.client.screen;

/** Scale-responsive browser geometry, isolated so overlap invariants can be unit tested. */
public record InventoryDrawerLayout(
        int width,
        int height,
        int top,
        int listTop,
        int visibleRows,
        int capacityTextY,
        int capacityBarY,
        int manageButtonY
) {
    public static final int VANILLA_WIDTH = 176;
    public static final int DRAWER_GAP = 20;
    public static final int ROW_HEIGHT = 18;
    private static final int IDEAL_WIDTH = 176;
    private static final int MIN_WIDTH = 118;
    private static final int VANILLA_HEIGHT = 166;

    public static InventoryDrawerLayout calculate(int screenWidth, int screenHeight) {
        int width = Math.max(MIN_WIDTH, Math.min(IDEAL_WIDTH,
                screenWidth - VANILLA_WIDTH - DRAWER_GAP - 8));
        int height = Math.max(VANILLA_HEIGHT, Math.min(210, screenHeight - 8));
        int top = (screenHeight - height) / 2;
        int manageButtonY = top + height - 23;
        int capacityBarY = manageButtonY - 13;
        int capacityTextY = capacityBarY - 11;
        int listTop = top + 49;
        int visibleRows = Math.max(1, (capacityTextY - listTop - 2) / ROW_HEIGHT);
        return new InventoryDrawerLayout(width, height, top, listTop, visibleRows,
                capacityTextY, capacityBarY, manageButtonY);
    }
}
