package com.cappleapple.stacksnotslots.inventory;

import com.cappleapple.stacksnotslots.StacksNotSlots;
import com.cappleapple.stacksnotslots.api.ExtractionResult;
import com.cappleapple.stacksnotslots.api.ICapacityInventory;
import com.cappleapple.stacksnotslots.api.InsertionRejection;
import com.cappleapple.stacksnotslots.api.InsertionResult;
import com.cappleapple.stacksnotslots.api.LogicalInventoryEntry;
import com.cappleapple.stacksnotslots.api.InventoryChangeListener;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.LongSupplier;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.util.INBTSerializable;

/**
 * The authoritative collection. Backing stacks are always legal vanilla stacks, but their list has no
 * gameplay-defined maximum. Capacity is the sole insertion limit.
 */
public final class DynamicCapacityInventory implements ICapacityInventory, INBTSerializable<CompoundTag> {
    public static final int DATA_VERSION = 1;
    private final ArrayList<ItemStack> backingStacks = new ArrayList<>();
    private final LongSupplier capacitySupplier;
    private final Runnable mutationListener;
    private final CopyOnWriteArrayList<InventoryChangeListener> listeners = new CopyOnWriteArrayList<>();
    private ListTag unresolvedEntries = new ListTag();
    private long usedCapacity;
    private long revision;
    private long observedHash;
    private long cachedEntriesRevision = -1;
    private List<LogicalInventoryEntry> cachedEntries = List.of();

    public DynamicCapacityInventory(LongSupplier capacitySupplier) {
        this(capacitySupplier, () -> {});
    }

    public DynamicCapacityInventory(LongSupplier capacitySupplier, Runnable mutationListener) {
        this.capacitySupplier = Objects.requireNonNull(capacitySupplier);
        this.mutationListener = Objects.requireNonNull(mutationListener);
    }

    @Override
    public long capacity() {
        return Math.max(0, capacitySupplier.getAsLong());
    }

    @Override
    public long usedCapacity() {
        return usedCapacity;
    }

    @Override
    public long revision() {
        return revision;
    }

    @Override
    public AutoCloseable addListener(InventoryChangeListener listener) {
        listeners.add(Objects.requireNonNull(listener));
        return () -> listeners.remove(listener);
    }

    @Override
    public List<LogicalInventoryEntry> entries() {
        if (cachedEntriesRevision == revision) return cachedEntries;
        Map<StackIdentity, Aggregate> aggregates = new LinkedHashMap<>();
        for (ItemStack stack : backingStacks) {
            StackIdentity key = new StackIdentity(stack);
            aggregates.computeIfAbsent(key, ignored -> new Aggregate(stack.copyWithCount(1))).add(stack.getCount());
        }
        cachedEntries = aggregates.values().stream()
                .map(value -> new LogicalInventoryEntry(value.representative, value.quantity, value.backingStackCount))
                .toList();
        cachedEntriesRevision = revision;
        return cachedEntries;
    }

    /** Returns defensive copies of every legal backing stack. */
    public List<ItemStack> backingStacks() {
        return backingStacks.stream().map(ItemStack::copy).toList();
    }

    /** Number of occupied synthetic slots. This grows with the represented collection and has no fixed ceiling. */
    public int syntheticSlotCount() {
        return backingStacks.size();
    }

    /** Occupied slots plus one append slot, unless the JVM list index space itself is exhausted. */
    public int compatibilitySlotCount() {
        return backingStacks.size() == Integer.MAX_VALUE ? Integer.MAX_VALUE : backingStacks.size() + 1;
    }

    public ItemStack syntheticStack(int slot) {
        return slot >= 0 && slot < backingStacks.size() ? backingStacks.get(slot).copy() : ItemStack.EMPTY;
    }

    /** Live reference used only by the isolated vanilla Inventory compatibility mixin. */
    public ItemStack vanillaStackReference(int slot) {
        return slot >= 0 && slot < backingStacks.size() ? backingStacks.get(slot) : ItemStack.EMPTY;
    }

    public ItemStack findVanillaStackReference(ItemStack prototype) {
        if (prototype.isEmpty()) return ItemStack.EMPTY;
        return backingStacks.stream().filter(stack -> ItemStack.isSameItemSameComponents(stack, prototype)).findFirst().orElse(ItemStack.EMPTY);
    }

    public int indexOf(ItemStack prototype) {
        if (prototype.isEmpty()) return -1;
        for (int i = 0; i < backingStacks.size(); i++) {
            ItemStack stack = backingStacks.get(i);
            if (stack == prototype || ItemStack.isSameItemSameComponents(stack, prototype)) return i;
        }
        return -1;
    }

    public void removeReference(ItemStack reference) {
        for (int i = 0; i < backingStacks.size(); i++) {
            if (backingStacks.get(i) != reference) continue;
            ItemStack removed = backingStacks.remove(i);
            usedCapacity = Math.max(0, usedCapacity - CapacityCosts.cost(removed, removed.getCount()));
            changed();
            return;
        }
    }

    public List<ItemStack> drain() {
        List<ItemStack> drained = backingStacks();
        clear();
        return drained;
    }

    public void tick(Player player) {
        for (int i = 0; i < backingStacks.size(); i++) {
            ItemStack stack = backingStacks.get(i);
            stack.inventoryTick(player.level(), player, i, i == player.getInventory().selected);
        }
        reconcileExternalMutations();
    }

    @Override
    public InsertionResult insert(ItemStack stack, boolean simulate) {
        if (stack == null || stack.isEmpty() || stack.getCount() <= 0) {
            return new InsertionResult(stack == null ? 0 : Math.max(0, stack.getCount()), 0, ItemStack.EMPTY, 0, InsertionRejection.INVALID_ITEM);
        }
        int requested = stack.getCount();
        long unitCost = CapacityCosts.unitCost(stack);
        long available = capacity() - usedCapacity;
        if (available < 0) {
            return rejected(stack, requested, InsertionRejection.OVER_CAPACITY);
        }
        int accepted = (int)Math.min(requested, Math.min(Integer.MAX_VALUE, available / unitCost));
        if (accepted <= 0) {
            return rejected(stack, requested, InsertionRejection.GLOBAL_CAPACITY);
        }
        if (!simulate) {
            addLegalStacks(stack, accepted);
            usedCapacity = saturatedAdd(usedCapacity, CapacityCosts.cost(stack, accepted));
            changed();
        }
        ItemStack remainder = accepted == requested ? ItemStack.EMPTY : stack.copyWithCount(requested - accepted);
        InsertionRejection rejection = accepted == requested ? InsertionRejection.NONE : InsertionRejection.GLOBAL_CAPACITY;
        return new InsertionResult(requested, accepted, remainder, CapacityCosts.cost(stack, accepted), rejection);
    }

    public InsertionResult insertAtMost(ItemStack stack, int maximumAccepted, InsertionRejection limitingReason, boolean simulate) {
        int allowed = Math.max(0, Math.min(stack.getCount(), maximumAccepted));
        if (allowed == 0) return rejected(stack, stack.getCount(), limitingReason);
        InsertionResult limited = insert(stack.copyWithCount(allowed), simulate);
        int accepted = limited.acceptedAmount();
        ItemStack remainder = accepted == stack.getCount() ? ItemStack.EMPTY : stack.copyWithCount(stack.getCount() - accepted);
        InsertionRejection reason = accepted < allowed ? limited.rejection() : accepted < stack.getCount() ? limitingReason : InsertionRejection.NONE;
        return new InsertionResult(stack.getCount(), accepted, remainder, limited.capacityConsumed(), reason);
    }

    @Override
    public ExtractionResult extract(ItemStack prototype, int amount, boolean simulate) {
        if (prototype == null || prototype.isEmpty() || amount <= 0) return new ExtractionResult(Math.max(0, amount), 0, List.of());
        int remaining = amount;
        ArrayList<ItemStack> result = new ArrayList<>();
        for (int index = 0; index < backingStacks.size() && remaining > 0; index++) {
            ItemStack stored = backingStacks.get(index);
            if (!ItemStack.isSameItemSameComponents(stored, prototype)) continue;
            int taken = Math.min(stored.getCount(), remaining);
            result.add(stored.copyWithCount(taken));
            remaining -= taken;
            if (!simulate) {
                stored.shrink(taken);
                usedCapacity = Math.max(0, usedCapacity - CapacityCosts.cost(stored, taken));
                if (stored.isEmpty()) backingStacks.remove(index--);
            }
        }
        int extracted = amount - remaining;
        if (!simulate && extracted > 0) changed();
        return new ExtractionResult(amount, extracted, result);
    }

    public ItemStack extractSyntheticSlot(int slot, int amount, boolean simulate) {
        if (slot < 0 || slot >= backingStacks.size() || amount <= 0) return ItemStack.EMPTY;
        ItemStack stored = backingStacks.get(slot);
        int taken = Math.min(amount, stored.getCount());
        ItemStack result = stored.copyWithCount(taken);
        if (!simulate) {
            stored.shrink(taken);
            usedCapacity = Math.max(0, usedCapacity - CapacityCosts.cost(result, taken));
            if (stored.isEmpty()) backingStacks.remove(slot);
            changed();
        }
        return result;
    }

    /** Implements IItemHandlerModifiable replacement atomically, including the append slot. */
    public void replaceSyntheticSlot(int slot, ItemStack replacement) {
        replaceSyntheticSlot(slot, replacement, true);
    }

    /**
     * Vanilla item use may transform an already-owned stack into a higher-cost result after the world
     * interaction has committed. Preserve that result even if it temporarily creates over-capacity state.
     */
    public void replaceSyntheticSlotFromItemUse(int slot, ItemStack replacement) {
        replaceSyntheticSlot(slot, replacement, false);
    }

    private void replaceSyntheticSlot(int slot, ItemStack replacement, boolean enforceCapacityIncrease) {
        if (slot < 0 || slot > backingStacks.size()) throw new IndexOutOfBoundsException("Synthetic slot " + slot);
        if (slot == backingStacks.size()) {
            if (replacement.isEmpty()) return;
            InsertionResult inserted = insert(replacement, false);
            if (!inserted.acceptedAll()) throw new IllegalArgumentException("Replacement exceeds inventory capacity");
            return;
        }
        ItemStack previous = backingStacks.get(slot);
        long previousCost = CapacityCosts.cost(previous, previous.getCount());
        long replacementCost = replacement.isEmpty() ? 0 : CapacityCosts.cost(replacement, replacement.getCount());
        long projected = saturatedAdd(Math.max(0, usedCapacity - previousCost), replacementCost);
        if (replacement.getCount() > replacement.getMaxStackSize()) throw new IllegalArgumentException("Synthetic slots must contain legal ItemStacks");
        if (enforceCapacityIncrease && projected > capacity() && projected > usedCapacity) {
            throw new IllegalArgumentException("Replacement exceeds inventory capacity");
        }
        if (replacement.isEmpty()) backingStacks.remove(slot);
        else backingStacks.set(slot, replacement.copy());
        usedCapacity = projected;
        changed();
    }

    public void clear() {
        if (backingStacks.isEmpty() && unresolvedEntries.isEmpty()) return;
        backingStacks.clear();
        unresolvedEntries = new ListTag();
        usedCapacity = 0;
        changed();
    }

    /** One-time lossless import path. It intentionally permits an over-capacity result. */
    public void importUnbounded(List<ItemStack> stacks) {
        boolean imported = false;
        for (ItemStack stack : stacks) {
            if (stack.isEmpty()) continue;
            addLegalStacks(stack, stack.getCount());
            imported = true;
        }
        if (imported) {
            recalculateCapacity();
            changed();
        }
    }

    /** Detects item-use and durability mutations made through live vanilla stack references. */
    public void reconcileExternalMutations() {
        boolean normalized = backingStacks.removeIf(ItemStack::isEmpty);
        for (int i = 0; i < backingStacks.size(); i++) {
            ItemStack stack = backingStacks.get(i);
            if (stack.getCount() <= stack.getMaxStackSize()) continue;
            int excess = stack.getCount() - stack.getMaxStackSize();
            stack.setCount(stack.getMaxStackSize());
            addLegalStacks(stack, excess);
            normalized = true;
        }
        long currentHash = calculateHash();
        if (normalized || currentHash != observedHash) {
            recalculateCapacity();
            changed();
        }
    }

    public boolean validate() {
        return validationErrors().isEmpty();
    }

    /** Returns actionable invariant failures without modifying inventory state. */
    public List<String> validationErrors() {
        ArrayList<String> errors = new ArrayList<>();
        long calculated = 0;
        for (int index = 0; index < backingStacks.size(); index++) {
            ItemStack stack = backingStacks.get(index);
            if (stack.isEmpty() || stack.getCount() <= 0) {
                errors.add("Backing index " + index + " is empty or non-positive");
                continue;
            }
            if (stack.getCount() > stack.getMaxStackSize()) {
                errors.add("Backing index " + index + " exceeds its legal max stack size");
            }
            calculated = saturatedAdd(calculated, CapacityCosts.cost(stack, stack.getCount()));
        }
        if (usedCapacity < 0) errors.add("Used capacity is negative: " + usedCapacity);
        if (calculated != usedCapacity) errors.add("Used capacity mismatch: cached=" + usedCapacity + ", calculated=" + calculated);
        return List.copyOf(errors);
    }

    public void loadNetworkSnapshot(List<ItemStack> stacks, long serverRevision) {
        backingStacks.clear();
        for (ItemStack stack : stacks) addDeserialized(stack);
        revision = Math.max(0, serverRevision);
        recalculateCapacity();
        observedHash = calculateHash();
        invalidateEntryCache();
    }

    public boolean applyNetworkDelta(long baseRevision, long serverRevision, int resultingSize, Map<Integer, ItemStack> changes) {
        if (revision != baseRevision || resultingSize < 0) return false;
        while (backingStacks.size() < resultingSize) backingStacks.add(ItemStack.EMPTY);
        for (Map.Entry<Integer, ItemStack> change : changes.entrySet()) {
            int index = change.getKey();
            if (index < 0 || index >= resultingSize) return false;
            backingStacks.set(index, change.getValue().copy());
        }
        while (backingStacks.size() > resultingSize) backingStacks.removeLast();
        if (backingStacks.stream().anyMatch(ItemStack::isEmpty)) return false;
        revision = Math.max(0, serverRevision);
        recalculateCapacity();
        observedHash = calculateHash();
        invalidateEntryCache();
        return validate();
    }

    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        CompoundTag root = new CompoundTag();
        root.putInt("Version", DATA_VERSION);
        ListTag items = new ListTag();
        for (ItemStack stack : backingStacks) items.add(stack.save(provider, new CompoundTag()));
        for (int i = 0; i < unresolvedEntries.size(); i++) items.add(unresolvedEntries.getCompound(i).copy());
        root.put("Items", items);
        root.putLong("Revision", revision);
        return root;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag root) {
        backingStacks.clear();
        unresolvedEntries = new ListTag();
        ListTag items = root.getList("Items", CompoundTag.TAG_COMPOUND);
        for (int i = 0; i < items.size(); i++) {
            CompoundTag encoded = items.getCompound(i);
            ItemStack.parse(provider, encoded).ifPresentOrElse(this::addDeserialized, () -> {
                unresolvedEntries.add(encoded.copy());
                StacksNotSlots.LOGGER.warn("Preserving unresolved inventory entry during load: {}", encoded);
            });
        }
        revision = Math.max(0, root.getLong("Revision"));
        recalculateCapacity();
        observedHash = calculateHash();
        invalidateEntryCache();
    }

    private void addDeserialized(ItemStack stack) {
        if (stack.isEmpty()) return;
        int remaining = stack.getCount();
        while (remaining > 0) {
            int amount = Math.min(remaining, stack.getMaxStackSize());
            backingStacks.add(stack.copyWithCount(amount));
            remaining -= amount;
        }
    }

    private void addLegalStacks(ItemStack incoming, int amount) {
        int remaining = amount;
        if (incoming.isStackable()) {
            for (ItemStack stored : backingStacks) {
                if (remaining == 0) break;
                if (!ItemStack.isSameItemSameComponents(stored, incoming)) continue;
                int moved = Math.min(remaining, stored.getMaxStackSize() - stored.getCount());
                if (moved > 0) {
                    stored.grow(moved);
                    remaining -= moved;
                }
            }
        }
        while (remaining > 0) {
            int moved = Math.min(remaining, incoming.getMaxStackSize());
            backingStacks.add(incoming.copyWithCount(moved));
            remaining -= moved;
        }
    }

    private void recalculateCapacity() {
        usedCapacity = 0;
        for (ItemStack stack : backingStacks) usedCapacity = saturatedAdd(usedCapacity, CapacityCosts.cost(stack, stack.getCount()));
    }

    private void changed() {
        revision = revision == Long.MAX_VALUE ? 0 : revision + 1;
        observedHash = calculateHash();
        invalidateEntryCache();
        mutationListener.run();
        for (InventoryChangeListener listener : listeners) {
            try {
                listener.inventoryChanged(this, revision);
            } catch (RuntimeException exception) {
                StacksNotSlots.LOGGER.error("Inventory change listener failed at revision {}", revision, exception);
            }
        }
    }

    private long calculateHash() {
        long hash = 1;
        for (ItemStack stack : backingStacks) hash = 31 * hash + 31L * ItemStack.hashItemAndComponents(stack) + stack.getCount();
        return hash;
    }

    private void invalidateEntryCache() {
        cachedEntriesRevision = -1;
        cachedEntries = List.of();
    }

    private static InsertionResult rejected(ItemStack stack, int requested, InsertionRejection reason) {
        return new InsertionResult(requested, 0, stack.copy(), 0, reason);
    }

    private static long saturatedAdd(long left, long right) {
        if (right > 0 && left > Long.MAX_VALUE - right) return Long.MAX_VALUE;
        return left + right;
    }

    private static final class Aggregate {
        private final ItemStack representative;
        private long quantity;
        private int backingStackCount;

        private Aggregate(ItemStack representative) { this.representative = representative; }
        private void add(int amount) { quantity += amount; backingStackCount++; }
    }

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
