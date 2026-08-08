package com.cappleapple.stacksnotslots.hotbar;

import com.cappleapple.stacksnotslots.api.LogicalInventoryEntry;
import com.cappleapple.stacksnotslots.category.CategoryDefinition;
import com.cappleapple.stacksnotslots.category.CategoryMatcher;
import com.cappleapple.stacksnotslots.category.PlayerCategoryData;
import com.cappleapple.stacksnotslots.inventory.DynamicCapacityInventory;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public final class HotbarBindings {
    public static final int SLOT_COUNT = 9;
    private final HotbarBinding[] bindings = new HotbarBinding[SLOT_COUNT];
    private final HotbarBinding[] cachedBindings = new HotbarBinding[SLOT_COUNT];
    private final ItemStack[] cachedSelections = new ItemStack[SLOT_COUNT];
    private final long[] cachedInventoryRevisions = new long[SLOT_COUNT];
    private final long[] cachedCategoryRevisions = new long[SLOT_COUNT];
    private final int[] cachedBackingIndexes = new int[SLOT_COUNT];

    public HotbarBindings() {
        for (int i = 0; i < bindings.length; i++) bindings[i] = HotbarBinding.empty();
        invalidateAll();
    }

    public HotbarBinding get(int slot) { return bindings[checkSlot(slot)]; }
    public void set(int slot, HotbarBinding binding) {
        int checked = checkSlot(slot);
        bindings[checked] = binding;
        invalidate(checked);
    }

    public ItemStack resolve(int slot, DynamicCapacityInventory inventory, PlayerCategoryData categories) {
        HotbarBinding binding = get(slot);
        if (cachedBindings[slot] == binding && cachedInventoryRevisions[slot] == inventory.revision()
                && cachedCategoryRevisions[slot] == categories.revision()) {
            return cachedSelections[slot].copy();
        }
        List<ItemStack> candidates = candidates(binding, inventory, categories);
        if (candidates.isEmpty()) {
            cache(slot, binding, inventory, categories, ItemStack.EMPTY);
            return ItemStack.EMPTY;
        }
        if (binding.selectedEntry() != null) {
            for (ItemStack candidate : candidates) {
                if (binding.selectedEntry().matches(candidate)) {
                    cache(slot, binding, inventory, categories, candidate);
                    return candidate.copy();
                }
            }
        }
        ItemStack selected = candidates.getFirst();
        bindings[slot] = binding.withSelected(selected);
        cache(slot, bindings[slot], inventory, categories, selected);
        return selected.copy();
    }

    /** Resolves a stable backing index for vanilla held-item access until the inventory revision changes. */
    public int resolveIndex(int slot, DynamicCapacityInventory inventory, PlayerCategoryData categories) {
        resolve(slot, inventory, categories);
        return cachedBackingIndexes[checkSlot(slot)];
    }

    public ItemStack cycle(int slot, int direction, DynamicCapacityInventory inventory, PlayerCategoryData categories) {
        HotbarBinding binding = get(slot);
        List<ItemStack> candidates = candidates(binding, inventory, categories);
        if (candidates.isEmpty()) return ItemStack.EMPTY;
        int current = -1;
        for (int i = 0; i < candidates.size(); i++) {
            if (binding.selectedEntry() != null && binding.selectedEntry().matches(candidates.get(i))) { current = i; break; }
        }
        int next = Math.floorMod(current + (direction < 0 ? -1 : 1), candidates.size());
        ItemStack selected = candidates.get(next);
        bindings[slot] = binding.withSelected(selected);
        invalidate(slot);
        return selected.copy();
    }

    private static List<ItemStack> candidates(HotbarBinding binding, DynamicCapacityInventory inventory, PlayerCategoryData categories) {
        ArrayList<LogicalInventoryEntry> matchingEntries = new ArrayList<>();
        if (binding.type() == BindingType.EMPTY || binding.target() == null) return List.of();
        CategoryDefinition category = binding.type() == BindingType.CATEGORY ? categories.find(binding.target()) : null;
        for (LogicalInventoryEntry entry : inventory.entries()) {
            ItemStack stack = entry.representative();
            boolean entryMatches = switch (binding.type()) {
                case ITEM -> BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(binding.target());
                case CATEGORY -> category != null && CategoryMatcher.matches(category, stack);
                case FILTER -> BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().contains(binding.target().getPath());
                case EMPTY -> false;
            };
            if (entryMatches) matchingEntries.add(entry);
        }
        // Registry IDs give client and dedicated server the same order regardless of language settings.
        matchingEntries.sort(Comparator.comparing(
                entry -> BuiltInRegistries.ITEM.getKey(entry.representative().getItem()).toString()));
        return matchingEntries.stream().map(entry -> {
            ItemStack stack = entry.representative();
            return stack.copyWithCount((int)Math.min(entry.quantity(), stack.getMaxStackSize()));
        }).toList();
    }

    public CompoundTag save(HolderLookup.Provider provider) {
        CompoundTag root = new CompoundTag();
        ListTag list = new ListTag();
        for (int slot = 0; slot < bindings.length; slot++) {
            HotbarBinding binding = bindings[slot];
            CompoundTag tag = new CompoundTag();
            tag.putByte("Slot", (byte)slot);
            tag.putString("Type", binding.type().name());
            if (binding.target() != null) tag.putString("Target", binding.target().toString());
            if (binding.selectedEntry() != null) tag.put("SelectedEntry", binding.selectedEntry().save(provider));
            list.add(tag);
        }
        root.put("Bindings", list);
        return root;
    }

    public void load(HolderLookup.Provider provider, CompoundTag root) {
        for (int i = 0; i < bindings.length; i++) bindings[i] = HotbarBinding.empty();
        ListTag list = root.getList("Bindings", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag tag = list.getCompound(i);
            int slot = tag.getByte("Slot");
            if (slot < 0 || slot >= SLOT_COUNT) continue;
            BindingType type;
            try { type = BindingType.valueOf(tag.getString("Type")); }
            catch (IllegalArgumentException ignored) { type = BindingType.EMPTY; }
            StackReference selected = tag.contains("SelectedEntry", Tag.TAG_COMPOUND)
                    ? StackReference.load(provider, tag.getCompound("SelectedEntry")).orElse(null)
                    : null;
            if (selected == null && tag.contains("Selected", Tag.TAG_STRING)) {
                ResourceLocation legacySelected = ResourceLocation.tryParse(tag.getString("Selected"));
                if (legacySelected != null) {
                    selected = BuiltInRegistries.ITEM.getOptional(legacySelected)
                            .map(item -> item.getDefaultInstance())
                            .filter(stack -> !stack.isEmpty())
                            .map(StackReference::of)
                            .orElse(null);
                }
            }
            bindings[slot] = new HotbarBinding(type, ResourceLocation.tryParse(tag.getString("Target")), selected);
        }
        invalidateAll();
    }

    private void cache(int slot, HotbarBinding binding, DynamicCapacityInventory inventory, PlayerCategoryData categories, ItemStack selected) {
        cachedBindings[slot] = binding;
        cachedInventoryRevisions[slot] = inventory.revision();
        cachedCategoryRevisions[slot] = categories.revision();
        cachedSelections[slot] = selected.copy();
        cachedBackingIndexes[slot] = selected.isEmpty() ? -1 : inventory.indexOf(selected);
    }

    private void invalidate(int slot) {
        cachedBindings[slot] = null;
        cachedSelections[slot] = ItemStack.EMPTY;
        cachedInventoryRevisions[slot] = Long.MIN_VALUE;
        cachedCategoryRevisions[slot] = Long.MIN_VALUE;
        cachedBackingIndexes[slot] = -1;
    }

    private void invalidateAll() {
        Arrays.fill(cachedBindings, null);
        Arrays.fill(cachedSelections, ItemStack.EMPTY);
        Arrays.fill(cachedInventoryRevisions, Long.MIN_VALUE);
        Arrays.fill(cachedCategoryRevisions, Long.MIN_VALUE);
        Arrays.fill(cachedBackingIndexes, -1);
    }

    private static int checkSlot(int slot) {
        if (slot < 0 || slot >= SLOT_COUNT) throw new IndexOutOfBoundsException("Hotbar slot " + slot);
        return slot;
    }
}
