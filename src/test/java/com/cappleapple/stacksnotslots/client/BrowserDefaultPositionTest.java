package com.cappleapple.stacksnotslots.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.cappleapple.stacksnotslots.config.ClientConfig;
import org.junit.jupiter.api.Test;

class BrowserDefaultPositionTest {
    @Test
    void bottomRightAlignsWithTheHotbarRow() {
        assertEquals(new BrowserDefaultPosition(330, 298),
                BrowserDefaultPosition.resolve(150, 100, 178, 222,
                        ClientConfig.BrowserDefaultPlacement.BOTTOM_RIGHT));
    }

    @Test
    void everyConfiguredAnchorUsesTheContainerBounds() {
        assertEquals(new BrowserDefaultPosition(128, 106), position(ClientConfig.BrowserDefaultPlacement.TOP_LEFT));
        assertEquals(new BrowserDefaultPosition(330, 106), position(ClientConfig.BrowserDefaultPlacement.TOP_RIGHT));
        assertEquals(new BrowserDefaultPosition(128, 298), position(ClientConfig.BrowserDefaultPlacement.BOTTOM_LEFT));
        assertEquals(new BrowserDefaultPosition(330, 298), position(ClientConfig.BrowserDefaultPlacement.BOTTOM_RIGHT));
        assertEquals(new BrowserDefaultPosition(128, 202), position(ClientConfig.BrowserDefaultPlacement.LEFT_CENTER));
        assertEquals(new BrowserDefaultPosition(330, 202), position(ClientConfig.BrowserDefaultPlacement.RIGHT_CENTER));
    }

    private static BrowserDefaultPosition position(ClientConfig.BrowserDefaultPlacement placement) {
        return BrowserDefaultPosition.resolve(150, 100, 178, 222, placement);
    }
}
