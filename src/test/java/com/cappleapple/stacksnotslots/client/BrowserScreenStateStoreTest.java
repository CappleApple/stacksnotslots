package com.cappleapple.stacksnotslots.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cappleapple.stacksnotslots.config.ClientConfig;
import org.junit.jupiter.api.Test;

class BrowserScreenStateStoreTest {
    @Test
    void stateRoundTripsWithItsConcreteScreenType() {
        BrowserScreenStateStore.State state = new BrowserScreenStateStore.State(
                "example.inventory.BackpackScreen", 125, 44, true, false, ClientConfig.BrowserDockSide.TOP);
        assertEquals(state, BrowserScreenStateStore.decode(BrowserScreenStateStore.encode(state)).orElseThrow());
    }

    @Test
    void malformedStateIsIgnored() {
        assertTrue(BrowserScreenStateStore.decode("v1|broken").isEmpty());
        assertTrue(BrowserScreenStateStore.decode("v1|Screen|x|2|true|true|LEFT").isEmpty());
    }
}
