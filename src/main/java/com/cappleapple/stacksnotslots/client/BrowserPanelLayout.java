package com.cappleapple.stacksnotslots.client;

import com.cappleapple.stacksnotslots.config.ClientConfig;

/** Responsive browser geometry derived from the handle, dock axis, and available screen space. */
record BrowserPanelLayout(
        int panelX, int panelY, int panelWidth, int panelHeight,
        int searchX, int searchY, int searchWidth,
        ButtonBounds category, ButtonBounds sort, ButtonBounds transfer,
        ButtonBounds direction, ButtonBounds manage, ButtonBounds settings,
        int contentX, int contentY, int contentWidth, int contentHeight,
        int visibleEntryCount, int gridColumns, int gridRows,
        int capacityTextY, int capacityBarY
) {
    static final int CONTROL_WIDTH = 20;
    static final int CONTROL_HEIGHT = 18;
    static final int HANDLE_WIDTH = 20;
    static final int HANDLE_HEIGHT = 18;
    static final int GRID_CELL = 20;
    static final int LIST_ROW = 18;
    static final int EDGE_MARGIN = 2;
    static final int PANEL_GAP = 2;
    private static final int SIDE_MIN_WIDTH = 68;
    private static final int SIDE_OVERHEAD_HEIGHT = 95;
    private static final int HORIZONTAL_FIXED_WIDTH = 48;
    private static final int HORIZONTAL_OVERHEAD_HEIGHT = 48;
    private static final int HORIZONTAL_CONTROL_RAIL_HEIGHT = 58;

    static BrowserPanelLayout calculate(
            int screenWidth, int screenHeight, int handleX, int handleY,
            ClientConfig.BrowserDockSide side, ClientConfig.BrowserViewMode viewMode,
            int configuredColumns, int configuredRows) {
        boolean horizontal = side == ClientConfig.BrowserDockSide.TOP || side == ClientConfig.BrowserDockSide.BOTTOM;
        return horizontal
                ? horizontal(screenWidth, screenHeight, handleX, handleY, side, viewMode, configuredColumns, configuredRows)
                : side(screenWidth, screenHeight, handleX, handleY, side, viewMode, configuredColumns, configuredRows);
    }

    static HandlePosition constrainHandle(
            int screenWidth, int screenHeight, int requestedX, int requestedY, ClientConfig.BrowserDockSide side) {
        int minimumX = 0;
        int physicalMaximumX = Math.max(0, screenWidth - HANDLE_WIDTH);
        int maximumX = physicalMaximumX;
        int minimumY = 0;
        int physicalMaximumY = Math.max(0, screenHeight - HANDLE_HEIGHT);
        int maximumY = physicalMaximumY;
        if (side == ClientConfig.BrowserDockSide.LEFT) {
            minimumX = EDGE_MARGIN + SIDE_MIN_WIDTH + PANEL_GAP;
        } else if (side == ClientConfig.BrowserDockSide.RIGHT) {
            maximumX -= EDGE_MARGIN + SIDE_MIN_WIDTH + PANEL_GAP;
        } else if (side == ClientConfig.BrowserDockSide.TOP) {
            minimumY = EDGE_MARGIN + minimumHorizontalHeight() + PANEL_GAP;
        } else {
            maximumY -= EDGE_MARGIN + minimumHorizontalHeight() + PANEL_GAP;
        }
        int x = minimumX <= maximumX ? clamp(requestedX, minimumX, maximumX) : physicalMaximumX / 2;
        int y = minimumY <= maximumY ? clamp(requestedY, minimumY, maximumY) : physicalMaximumY / 2;
        return new HandlePosition(x, y);
    }

    private static BrowserPanelLayout side(
            int screenWidth, int screenHeight, int handleX, int handleY,
            ClientConfig.BrowserDockSide side, ClientConfig.BrowserViewMode viewMode,
            int configuredColumns, int configuredRows) {
        int availableWidth = side == ClientConfig.BrowserDockSide.LEFT
                ? handleX - PANEL_GAP - EDGE_MARGIN
                : screenWidth - EDGE_MARGIN - handleX - HANDLE_WIDTH - PANEL_GAP;
        int maximumRows = Math.max(1, (screenHeight - EDGE_MARGIN * 2 - SIDE_OVERHEAD_HEIGHT)
                / (viewMode == ClientConfig.BrowserViewMode.GRID ? GRID_CELL : LIST_ROW));
        int rows = Math.max(1, Math.min(configuredRows, maximumRows));
        int columns;
        int contentWidth;
        int contentHeight;
        int panelWidth;
        if (viewMode == ClientConfig.BrowserViewMode.GRID) {
            columns = Math.max(1, Math.min(configuredColumns, Math.max(1, (availableWidth - 8) / GRID_CELL)));
            contentWidth = columns * GRID_CELL;
            contentHeight = rows * GRID_CELL;
            panelWidth = Math.max(SIDE_MIN_WIDTH, contentWidth + 8);
        } else {
            columns = 1;
            panelWidth = Math.max(SIDE_MIN_WIDTH, Math.min(170, availableWidth));
            contentWidth = panelWidth - 8;
            contentHeight = rows * LIST_ROW;
        }
        int panelHeight = SIDE_OVERHEAD_HEIGHT + contentHeight;
        int panelX = side == ClientConfig.BrowserDockSide.LEFT
                ? handleX - PANEL_GAP - panelWidth : handleX + HANDLE_WIDTH + PANEL_GAP;
        int panelY = clamp(handleY + HANDLE_HEIGHT / 2 - panelHeight / 2,
                EDGE_MARGIN, Math.max(EDGE_MARGIN, screenHeight - EDGE_MARGIN - panelHeight));
        int controlsY = panelY + 27;
        int contentX = viewMode == ClientConfig.BrowserViewMode.GRID
                ? panelX + (panelWidth - contentWidth) / 2 : panelX + 4;
        int contentY = panelY + 49;
        int capacityTextY = contentY + contentHeight + 3;
        int capacityBarY = capacityTextY + 11;
        int bottomY = capacityBarY + 7;
        return base(panelX, panelY, panelWidth, panelHeight,
                new ButtonBounds(panelX + 2, controlsY), new ButtonBounds(panelX + 24, controlsY),
                new ButtonBounds(panelX + panelWidth - 22, controlsY),
                new ButtonBounds(panelX + 2, bottomY), new ButtonBounds(panelX + 24, bottomY),
                new ButtonBounds(panelX + panelWidth - 22, bottomY),
                contentX, contentY, contentWidth, contentHeight, rows * columns, columns, rows,
                capacityTextY, capacityBarY);
    }

    private static BrowserPanelLayout horizontal(
            int screenWidth, int screenHeight, int handleX, int handleY,
            ClientConfig.BrowserDockSide side, ClientConfig.BrowserViewMode viewMode,
            int configuredColumns, int configuredRows) {
        int availableHeight = side == ClientConfig.BrowserDockSide.TOP
                ? handleY - PANEL_GAP - EDGE_MARGIN
                : screenHeight - EDGE_MARGIN - handleY - HANDLE_HEIGHT - PANEL_GAP;
        int maximumColumns = Math.max(1, (screenWidth - EDGE_MARGIN * 2 - HORIZONTAL_FIXED_WIDTH)
                / (viewMode == ClientConfig.BrowserViewMode.GRID ? GRID_CELL : 1));
        int maximumRows = Math.max(1, (availableHeight - HORIZONTAL_OVERHEAD_HEIGHT)
                / (viewMode == ClientConfig.BrowserViewMode.GRID ? GRID_CELL : LIST_ROW));
        int desiredColumns = viewMode == ClientConfig.BrowserViewMode.GRID ? configuredRows : 1;
        int desiredRows = viewMode == ClientConfig.BrowserViewMode.GRID ? configuredColumns : configuredRows;
        int columns = Math.max(1, Math.min(desiredColumns, maximumColumns));
        int rows = Math.max(1, Math.min(desiredRows, maximumRows));
        int contentWidth = viewMode == ClientConfig.BrowserViewMode.GRID
                ? columns * GRID_CELL : Math.min(162, screenWidth - EDGE_MARGIN * 2 - HORIZONTAL_FIXED_WIDTH);
        int contentHeight = rows * (viewMode == ClientConfig.BrowserViewMode.GRID ? GRID_CELL : LIST_ROW);
        int sectionHeight = Math.max(contentHeight, HORIZONTAL_CONTROL_RAIL_HEIGHT);
        int panelWidth = contentWidth + HORIZONTAL_FIXED_WIDTH;
        int panelHeight = HORIZONTAL_OVERHEAD_HEIGHT + sectionHeight;
        int panelX = clamp(handleX + HANDLE_WIDTH / 2 - panelWidth / 2,
                EDGE_MARGIN, Math.max(EDGE_MARGIN, screenWidth - EDGE_MARGIN - panelWidth));
        int panelY = side == ClientConfig.BrowserDockSide.TOP
                ? handleY - PANEL_GAP - panelHeight : handleY + HANDLE_HEIGHT + PANEL_GAP;
        int contentX = panelX + 24;
        int contentY = panelY + 27;
        int capacityTextY = contentY + sectionHeight + 3;
        int capacityBarY = capacityTextY + 11;
        return base(panelX, panelY, panelWidth, panelHeight,
                new ButtonBounds(panelX + 2, contentY), new ButtonBounds(panelX + 2, contentY + 20),
                new ButtonBounds(panelX + 2, contentY + 40),
                new ButtonBounds(panelX + panelWidth - 22, contentY),
                new ButtonBounds(panelX + panelWidth - 22, contentY + 20),
                new ButtonBounds(panelX + panelWidth - 22, contentY + 40),
                contentX, contentY, contentWidth, contentHeight, rows * columns, columns, rows,
                capacityTextY, capacityBarY);
    }

    private static BrowserPanelLayout base(
            int panelX, int panelY, int panelWidth, int panelHeight,
            ButtonBounds category, ButtonBounds sort, ButtonBounds transfer,
            ButtonBounds direction, ButtonBounds manage, ButtonBounds settings,
            int contentX, int contentY, int contentWidth, int contentHeight,
            int visibleEntryCount, int gridColumns, int gridRows,
            int capacityTextY, int capacityBarY) {
        return new BrowserPanelLayout(panelX, panelY, panelWidth, panelHeight,
                panelX + 4, panelY + 5, panelWidth - 8,
                category, sort, transfer, direction, manage, settings,
                contentX, contentY, contentWidth, contentHeight,
                visibleEntryCount, gridColumns, gridRows, capacityTextY, capacityBarY);
    }

    private static int minimumHorizontalHeight() {
        return HORIZONTAL_OVERHEAD_HEIGHT + HORIZONTAL_CONTROL_RAIL_HEIGHT;
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    record ButtonBounds(int x, int y) {
        boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX < x + CONTROL_WIDTH && mouseY >= y && mouseY < y + CONTROL_HEIGHT;
        }
    }

    record HandlePosition(int x, int y) {}
}
