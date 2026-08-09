package com.cappleapple.stacksnotslots.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.cappleapple.stacksnotslots.config.ClientConfig;
import org.junit.jupiter.api.Test;

class BrowserGridDimensionsTest {
    @Test
    void topAndBottomTransposeConfiguredColumnsAndRows() {
        assertEquals(new BrowserGridDimensions(4, 6), dimensions(ClientConfig.BrowserDockSide.LEFT));
        assertEquals(new BrowserGridDimensions(4, 6), dimensions(ClientConfig.BrowserDockSide.RIGHT));
        assertEquals(new BrowserGridDimensions(6, 4), dimensions(ClientConfig.BrowserDockSide.TOP));
        assertEquals(new BrowserGridDimensions(6, 4), dimensions(ClientConfig.BrowserDockSide.BOTTOM));
    }

    @Test
    void verticalSpaceStillLimitsTheTransposedRowCount() {
        assertEquals(new BrowserGridDimensions(6, 2),
                BrowserGridDimensions.forDock(4, 6, 2, ClientConfig.BrowserDockSide.TOP));
    }

    private static BrowserGridDimensions dimensions(ClientConfig.BrowserDockSide side) {
        return BrowserGridDimensions.forDock(4, 6, 20, side);
    }
}
