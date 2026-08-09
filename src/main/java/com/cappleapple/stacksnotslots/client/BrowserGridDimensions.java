package com.cappleapple.stacksnotslots.client;

import com.cappleapple.stacksnotslots.config.ClientConfig;

/** Resolves configured grid dimensions for the browser's current opening axis. */
public record BrowserGridDimensions(int columns, int rows) {
    public static BrowserGridDimensions forDock(
            int configuredColumns, int configuredRows, int maximumVisibleRows, ClientConfig.BrowserDockSide side) {
        boolean horizontal = side == ClientConfig.BrowserDockSide.TOP || side == ClientConfig.BrowserDockSide.BOTTOM;
        int columns = horizontal ? configuredRows : configuredColumns;
        int requestedRows = horizontal ? configuredColumns : configuredRows;
        return new BrowserGridDimensions(Math.max(1, columns), Math.max(1, Math.min(requestedRows, maximumVisibleRows)));
    }
}
