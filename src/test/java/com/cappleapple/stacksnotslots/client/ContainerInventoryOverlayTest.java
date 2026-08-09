package com.cappleapple.stacksnotslots.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ContainerInventoryOverlayTest {
    @Test
    void handleRequiresIntentionalMovementBeforeDragging() {
        assertFalse(ContainerInventoryOverlay.exceedsDragThreshold(40, 40, 41, 40));
        assertFalse(ContainerInventoryOverlay.exceedsDragThreshold(40, 40, 43, 43));
        assertTrue(ContainerInventoryOverlay.exceedsDragThreshold(40, 40, 45, 40));
    }

    @Test
    void screenStateKeyDoesNotRequireMenuTypeMetadata() {
        assertEquals("InventoryScreen#InventoryMenu#176x166",
                ContainerInventoryOverlay.screenStateKey("InventoryScreen", "InventoryMenu", 176, 166));
    }
}
