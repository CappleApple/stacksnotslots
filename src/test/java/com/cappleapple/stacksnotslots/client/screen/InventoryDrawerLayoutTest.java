package com.cappleapple.stacksnotslots.client.screen;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class InventoryDrawerLayoutTest {
    @Test
    void itemRowsNeverOverlapCapacityOrControlsAtSupportedGuiSizes() {
        for (int width : new int[] {320, 360, 480, 854}) {
            for (int height : new int[] {180, 200, 240, 480}) {
                InventoryDrawerLayout layout = InventoryDrawerLayout.calculate(width, height);
                int listBottom = layout.listTop() + layout.visibleRows() * InventoryDrawerLayout.ROW_HEIGHT;
                assertTrue(layout.visibleRows() >= 1);
                assertTrue(listBottom <= layout.capacityTextY() - 2);
                assertTrue(layout.capacityTextY() + 9 <= layout.capacityBarY());
                assertTrue(layout.capacityBarY() + 6 < layout.manageButtonY());
                assertTrue(layout.width() + InventoryDrawerLayout.VANILLA_WIDTH
                        + InventoryDrawerLayout.DRAWER_GAP <= Math.max(314, width - 8));
            }
        }
    }
}
