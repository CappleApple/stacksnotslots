package com.cappleapple.stacksnotslots.client;

import com.cappleapple.stacksnotslots.config.ClientConfig;

/** Resolves the first handle position for a container-screen type that has no saved state. */
record BrowserDefaultPosition(int x, int y) {
    private static final int HOTBAR_BOTTOM_PADDING = 24;
    private static final int TOP_PADDING = 6;

    static BrowserDefaultPosition resolve(
            int guiLeft, int guiTop, int guiWidth, int guiHeight,
            ClientConfig.BrowserDefaultPlacement placement) {
        int leftX = guiLeft - BrowserPanelLayout.HANDLE_WIDTH - BrowserPanelLayout.PANEL_GAP;
        int rightX = guiLeft + guiWidth + BrowserPanelLayout.PANEL_GAP;
        int topY = guiTop + TOP_PADDING;
        int centerY = guiTop + guiHeight / 2 - BrowserPanelLayout.HANDLE_HEIGHT / 2;
        int bottomY = guiTop + guiHeight - HOTBAR_BOTTOM_PADDING;
        return switch (placement) {
            case BOTTOM_RIGHT -> new BrowserDefaultPosition(rightX, bottomY);
            case RIGHT_CENTER -> new BrowserDefaultPosition(rightX, centerY);
            case TOP_RIGHT -> new BrowserDefaultPosition(rightX, topY);
            case BOTTOM_LEFT -> new BrowserDefaultPosition(leftX, bottomY);
            case LEFT_CENTER -> new BrowserDefaultPosition(leftX, centerY);
            case TOP_LEFT -> new BrowserDefaultPosition(leftX, topY);
        };
    }
}
