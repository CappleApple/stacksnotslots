package com.cappleapple.stacksnotslots.data;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.SharedConstants;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class PlayerInventoryDataTest {
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }

    @Test
    void pickupToHotbarPreferencePersistsAndLegacyDataDefaultsOn() {
        RegistryAccess.Frozen access = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        PlayerInventoryData original = new PlayerInventoryData(null);
        original.setPickupIntoHotbar(false);

        PlayerInventoryData loaded = new PlayerInventoryData(null);
        loaded.loadMetadata(access, original.saveMetadata(access));
        assertFalse(loaded.pickupIntoHotbar());

        PlayerInventoryData legacy = new PlayerInventoryData(null);
        legacy.loadMetadata(access, new net.minecraft.nbt.CompoundTag());
        assertTrue(legacy.pickupIntoHotbar());
    }
}
