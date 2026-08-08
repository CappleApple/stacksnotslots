package com.cappleapple.stacksnotslots.compat;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class UnifiedInventoryMenuPolicyTest {
    @Test
    void suppressesOnlyStorageToStorageMoves() {
        assertTrue(UnifiedInventoryMenuPolicy.suppressStorageToStorageShiftClick(9, false));
        assertTrue(UnifiedInventoryMenuPolicy.suppressStorageToStorageShiftClick(44, false));
        assertFalse(UnifiedInventoryMenuPolicy.suppressStorageToStorageShiftClick(9, true));
        assertFalse(UnifiedInventoryMenuPolicy.suppressStorageToStorageShiftClick(0, false));
        assertFalse(UnifiedInventoryMenuPolicy.suppressStorageToStorageShiftClick(45, false));
    }
}
