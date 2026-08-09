package com.cappleapple.stacksnotslots.hotbar;

import com.cappleapple.stacksnotslots.category.CategoryDefinition;
import com.cappleapple.stacksnotslots.category.CategoryMatcher;
import com.cappleapple.stacksnotslots.category.PlayerCategoryData;
import com.cappleapple.stacksnotslots.inventory.DynamicCapacityInventory;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** Category-cycle metadata. Bindings never own, project, lock, or automatically replace hotbar stacks. */
public final class HotbarBindings {
    public static final int SLOT_COUNT = 9;
    private final HotbarBinding[] bindings = new HotbarBinding[SLOT_COUNT];

    public HotbarBindings() {
        for (int i = 0; i < bindings.length; i++) bindings[i] = HotbarBinding.empty();
    }

    public HotbarBinding get(int slot) { return bindings[checkSlot(slot)]; }

    public void set(int slot, HotbarBinding binding) {
        bindings[checkSlot(slot)] = binding.type() == BindingType.CATEGORY
                ? binding : HotbarBinding.empty();
    }

    /**
     * On an explicit cycle key press, swaps the selected hotbar position with the next distinct
     * matching backend/main-grid stack. Other interactions are completely unaffected by bindings.
     */
    public ItemStack cycle(int slot, int direction, DynamicCapacityInventory inventory, PlayerCategoryData categories) {
        HotbarBinding binding = get(slot);
        if (binding.type() != BindingType.CATEGORY || binding.target() == null) {
            return inventory.syntheticStack(slot);
        }
        CategoryDefinition category = categories.find(binding.target());
        if (category == null || !category.enabled()) return inventory.syntheticStack(slot);

        LinkedHashMap<StackIdentity, Candidate> distinct = new LinkedHashMap<>();
        ItemStack currentStack = inventory.syntheticStack(slot);
        if (CategoryMatcher.matches(category, currentStack)) {
            distinct.put(new StackIdentity(currentStack), new Candidate(slot, currentStack));
        }
        for (int index = 9; index < inventory.syntheticSlotCount(); index++) {
            ItemStack candidate = inventory.syntheticStack(index);
            if (!CategoryMatcher.matches(category, candidate)) continue;
            distinct.putIfAbsent(new StackIdentity(candidate), new Candidate(index, candidate));
        }
        if (distinct.isEmpty()) return currentStack;

        ArrayList<Candidate> candidates = new ArrayList<>(distinct.values());
        candidates.sort(Comparator
                .comparing((Candidate candidate) -> BuiltInRegistries.ITEM.getKey(candidate.stack().getItem()).toString())
                .thenComparingInt(candidate -> ItemStack.hashItemAndComponents(candidate.stack())));
        int current = -1;
        for (int index = 0; index < candidates.size(); index++) {
            ItemStack candidate = candidates.get(index).stack();
            if (binding.selectedEntry() != null && binding.selectedEntry().matches(candidate)
                    || binding.selectedEntry() == null && ItemStack.isSameItemSameComponents(currentStack, candidate)) {
                current = index;
                break;
            }
        }
        int next = current < 0
                ? (direction < 0 ? candidates.size() - 1 : 0)
                : Math.floorMod(current + (direction < 0 ? -1 : 1), candidates.size());
        Candidate selected = candidates.get(next);
        if (selected.index() != slot) inventory.swapSyntheticSlots(slot, selected.index());
        ItemStack result = inventory.syntheticStack(slot);
        bindings[slot] = binding.withSelected(result);
        return result;
    }

    public CompoundTag save(HolderLookup.Provider provider) {
        return save(provider, true);
    }

    /** Saves only durable category assignments; the currently cycled item is runtime state. */
    public CompoundTag saveClientState(HolderLookup.Provider provider) {
        return save(provider, false);
    }

    private CompoundTag save(HolderLookup.Provider provider, boolean includeSelectedEntry) {
        CompoundTag root = new CompoundTag();
        ListTag list = new ListTag();
        for (int slot = 0; slot < bindings.length; slot++) {
            HotbarBinding binding = bindings[slot];
            CompoundTag tag = new CompoundTag();
            tag.putByte("Slot", (byte)slot);
            tag.putString("Type", binding.type().name());
            if (binding.target() != null) tag.putString("Target", binding.target().toString());
            if (includeSelectedEntry && binding.selectedEntry() != null) {
                tag.put("SelectedEntry", binding.selectedEntry().save(provider));
            }
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
            if (slot < 0 || slot >= SLOT_COUNT || !BindingType.CATEGORY.name().equals(tag.getString("Type"))) continue;
            ResourceLocation target = ResourceLocation.tryParse(tag.getString("Target"));
            if (target == null) continue;
            StackReference selected = tag.contains("SelectedEntry", Tag.TAG_COMPOUND)
                    ? StackReference.load(provider, tag.getCompound("SelectedEntry")).orElse(null)
                    : null;
            bindings[slot] = new HotbarBinding(BindingType.CATEGORY, target, selected);
        }
    }

    private static int checkSlot(int slot) {
        if (slot < 0 || slot >= SLOT_COUNT) throw new IndexOutOfBoundsException("Hotbar slot " + slot);
        return slot;
    }

    private record Candidate(int index, ItemStack stack) {}

    private static final class StackIdentity {
        private final ItemStack stack;
        private final int hash;

        private StackIdentity(ItemStack stack) {
            this.stack = stack.copyWithCount(1);
            this.hash = ItemStack.hashItemAndComponents(stack);
        }

        @Override public int hashCode() { return hash; }
        @Override public boolean equals(Object other) {
            return other instanceof StackIdentity identity && ItemStack.isSameItemSameComponents(stack, identity.stack);
        }
    }
}
