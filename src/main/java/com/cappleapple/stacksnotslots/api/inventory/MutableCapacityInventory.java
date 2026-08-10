package com.cappleapple.stacksnotslots.api.inventory;

import com.cappleapple.stacksnotslots.api.ICapacityInventory;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;

/** Mutable, persistable capacity inventory created by {@link InventoryFactory}. */
public interface MutableCapacityInventory extends ICapacityInventory {
    void clear();
    CompoundTag serializeNBT(HolderLookup.Provider provider);
    void deserializeNBT(HolderLookup.Provider provider, CompoundTag root);
    InventorySnapshot snapshot();
    void applySnapshot(InventorySnapshot snapshot);
}
