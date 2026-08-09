package com.cappleapple.stacksnotslots.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cappleapple.stacksnotslots.config.ClientConfig;
import org.junit.jupiter.api.Test;

class BrowserPanelLayoutTest {
    @Test
    void everyDockFitsBetweenTheHandleAndScreenEdge() {
        for (int screenWidth : new int[] {220, 320, 480, 854}) {
            for (int screenHeight : new int[] {180, 240, 320, 480}) {
                for (ClientConfig.BrowserDockSide side : ClientConfig.BrowserDockSide.values()) {
                    for (ClientConfig.BrowserViewMode viewMode : ClientConfig.BrowserViewMode.values()) {
                        for (int[] requested : new int[][] {{0, 0}, {screenWidth / 2, screenHeight / 2}, {screenWidth, screenHeight}}) {
                            BrowserPanelLayout.HandlePosition handle = BrowserPanelLayout.constrainHandle(
                                    screenWidth, screenHeight, requested[0], requested[1], side);
                            BrowserPanelLayout layout = BrowserPanelLayout.calculate(
                                    screenWidth, screenHeight, handle.x(), handle.y(), side, viewMode, 4, 6);
                            assertTrue(layout.visibleEntryCount() >= 1);
                            assertTrue(layout.panelX() >= BrowserPanelLayout.EDGE_MARGIN);
                            assertTrue(layout.panelY() >= BrowserPanelLayout.EDGE_MARGIN);
                            assertTrue(layout.panelX() + layout.panelWidth()
                                    <= screenWidth - BrowserPanelLayout.EDGE_MARGIN);
                            assertTrue(layout.panelY() + layout.panelHeight()
                                    <= screenHeight - BrowserPanelLayout.EDGE_MARGIN);
                            switch (side) {
                                case LEFT -> assertEquals(handle.x() - BrowserPanelLayout.PANEL_GAP,
                                        layout.panelX() + layout.panelWidth());
                                case RIGHT -> assertEquals(handle.x() + BrowserPanelLayout.HANDLE_WIDTH
                                        + BrowserPanelLayout.PANEL_GAP, layout.panelX());
                                case TOP -> assertEquals(handle.y() - BrowserPanelLayout.PANEL_GAP,
                                        layout.panelY() + layout.panelHeight());
                                case BOTTOM -> assertEquals(handle.y() + BrowserPanelLayout.HANDLE_HEIGHT
                                        + BrowserPanelLayout.PANEL_GAP, layout.panelY());
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    void horizontalDockMovesControlRowsToSideRails() {
        BrowserPanelLayout.HandlePosition handle = BrowserPanelLayout.constrainHandle(
                480, 320, 240, 160, ClientConfig.BrowserDockSide.TOP);
        BrowserPanelLayout layout = BrowserPanelLayout.calculate(480, 320, handle.x(), handle.y(),
                ClientConfig.BrowserDockSide.TOP, ClientConfig.BrowserViewMode.GRID, 4, 6);
        assertEquals(layout.category().x(), layout.sort().x());
        assertEquals(layout.sort().x(), layout.transfer().x());
        assertTrue(layout.category().y() < layout.sort().y());
        assertTrue(layout.sort().y() < layout.transfer().y());
        assertEquals(layout.direction().x(), layout.manage().x());
        assertEquals(layout.manage().x(), layout.settings().x());
        assertTrue(layout.searchY() < layout.contentY());
    }

    @Test
    void availableRoomReducesGridBeforeMovingPastTheHandle() {
        BrowserPanelLayout.HandlePosition handle = BrowserPanelLayout.constrainHandle(
                220, 180, 140, 90, ClientConfig.BrowserDockSide.RIGHT);
        BrowserPanelLayout layout = BrowserPanelLayout.calculate(220, 180, handle.x(), handle.y(),
                ClientConfig.BrowserDockSide.RIGHT, ClientConfig.BrowserViewMode.GRID, 16, 20);
        assertTrue(layout.gridColumns() >= 1 && layout.gridColumns() < 16);
        assertTrue(layout.gridRows() >= 1 && layout.gridRows() < 20);
    }
}
