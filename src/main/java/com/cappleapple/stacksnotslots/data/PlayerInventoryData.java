package com.cappleapple.stacksnotslots.data;

import com.cappleapple.stacksnotslots.attribute.ModAttributes;
import com.cappleapple.stacksnotslots.category.PlayerCategoryData;
import com.cappleapple.stacksnotslots.category.SortMode;
import com.cappleapple.stacksnotslots.hotbar.HotbarBindings;
import com.cappleapple.stacksnotslots.inventory.DynamicCapacityInventory;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import net.neoforged.neoforge.common.util.INBTSerializable;

public final class PlayerInventoryData implements INBTSerializable<CompoundTag> {
    private final Player owner;
    private final DynamicCapacityInventory inventory;
    private final PlayerCategoryData categories = new PlayerCategoryData();
    private final HotbarBindings hotbar = new HotbarBindings();
    private boolean migratedVanillaInventory;
    private boolean initializedCapacityBase;
    private SortMode inventorySortPreference = SortMode.NAME_ASCENDING;
    private ResourceLocation selectedCategoryPreference;
    private boolean pickupIntoHotbar = true;

    public PlayerInventoryData(Player owner) {
        this.owner = owner;
        this.inventory = new DynamicCapacityInventory(this::effectiveCapacity, this::onInventoryChanged);
    }

    public DynamicCapacityInventory inventory() { return inventory; }
    public PlayerCategoryData categories() { return categories; }
    public HotbarBindings hotbar() { return hotbar; }
    public boolean migratedVanillaInventory() { return migratedVanillaInventory; }
    public void setMigratedVanillaInventory() { migratedVanillaInventory = true; }
    public boolean initializedCapacityBase() { return initializedCapacityBase; }
    public void setInitializedCapacityBase() { initializedCapacityBase = true; }
    public SortMode inventorySortPreference() { return inventorySortPreference; }
    public void setInventorySortPreference(SortMode preference) { inventorySortPreference = preference; }
    public @Nullable ResourceLocation selectedCategoryPreference() { return selectedCategoryPreference; }
    public void setSelectedCategoryPreference(@Nullable ResourceLocation preference) { selectedCategoryPreference = preference; }
    public boolean pickupIntoHotbar() { return pickupIntoHotbar; }
    public void setPickupIntoHotbar(boolean value) { pickupIntoHotbar = value; }

    public CompoundTag saveMetadata(HolderLookup.Provider provider) {
        CompoundTag root = new CompoundTag();
        root.put("Categories", categories.save());
        root.put("Hotbar", hotbar.save(provider));
        root.putBoolean("MigratedVanillaInventory", migratedVanillaInventory);
        root.putBoolean("InitializedCapacityBase", initializedCapacityBase);
        root.putString("InventorySortPreference", inventorySortPreference.name());
        if (selectedCategoryPreference != null) root.putString("SelectedCategoryPreference", selectedCategoryPreference.toString());
        root.putBoolean("PickupIntoHotbar", pickupIntoHotbar);
        return root;
    }

    public void loadMetadata(HolderLookup.Provider provider, CompoundTag root) {
        categories.load(root.getCompound("Categories"));
        hotbar.load(provider, root.getCompound("Hotbar"));
        loadUiPreferences(root);
    }

    public long effectiveCapacity() {
        return Math.max(0, (long)Math.floor(owner.getAttributeValue(ModAttributes.INVENTORY_CAPACITY)));
    }

    private void onInventoryChanged() {
        if (owner != null && !owner.level().isClientSide) ModAttachments.markDirty(owner);
    }

    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        CompoundTag root = new CompoundTag();
        root.put("Inventory", inventory.serializeNBT(provider));
        root.put("Categories", categories.save());
        root.put("Hotbar", hotbar.save(provider));
        root.putBoolean("MigratedVanillaInventory", migratedVanillaInventory);
        root.putBoolean("InitializedCapacityBase", initializedCapacityBase);
        root.putString("InventorySortPreference", inventorySortPreference.name());
        if (selectedCategoryPreference != null) root.putString("SelectedCategoryPreference", selectedCategoryPreference.toString());
        root.putBoolean("PickupIntoHotbar", pickupIntoHotbar);
        return root;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag root) {
        inventory.deserializeNBT(provider, root.getCompound("Inventory"));
        categories.load(root.getCompound("Categories"));
        hotbar.load(provider, root.getCompound("Hotbar"));
        migratedVanillaInventory = root.getBoolean("MigratedVanillaInventory");
        initializedCapacityBase = root.getBoolean("InitializedCapacityBase");
        loadUiPreferences(root);
    }

    private void loadUiPreferences(CompoundTag root) {
        try {
            inventorySortPreference = SortMode.valueOf(root.getString("InventorySortPreference"));
        } catch (IllegalArgumentException ignored) {
            inventorySortPreference = SortMode.NAME_ASCENDING;
        }
        selectedCategoryPreference = ResourceLocation.tryParse(root.getString("SelectedCategoryPreference"));
        pickupIntoHotbar = !root.contains("PickupIntoHotbar") || root.getBoolean("PickupIntoHotbar");
    }
}
