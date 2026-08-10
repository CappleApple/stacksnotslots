package com.cappleapple.stacksnotslots.api;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class CapacityAmountTest {
    @Test
    void arithmeticIsExactForUnrelatedDenominators() {
        CapacityAmount total = CapacityAmount.fraction(1, 2).add(CapacityAmount.fraction(2, 3));

        assertEquals(CapacityAmount.fraction(7, 6), total);
        assertEquals(1, total.floorToLong());
        assertEquals(2, total.ceilToLong());
    }

    @Test
    void divisionFloorsWithoutFloatingPointRounding() {
        CapacityAmount available = CapacityAmount.of(1);

        assertEquals(2, available.divideFloorToLong(CapacityAmount.fraction(1, 2)));
        assertEquals(1, available.divideFloorToLong(CapacityAmount.fraction(2, 3)));
    }
}
