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
import java.util.function.Predicate;
import java.util.function.LongSupplier;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.util.INBTSerializable;

/**
 * The authoritative collection. Occupied backing entries are always legal vanilla stacks, while empty
 * entries retain explicit compatibility-slot placement. The indexed extent has no gameplay-defined maximum;
 * capacity is the sole insertion limit.
 */
public final class DynamicCapacityInventory implements ICapacityInventory, INBTSerializable<CompoundTag> {
    public static final int DATA_VERSION = 2;
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
            if (stack.isEmpty()) continue;
            StackIdentity key = new StackIdentity(stack);
            aggregates.computeIfAbsent(key, ignored -> new Aggregate(stack.copyWithCount(1))).add(stack.getCount());
        }
        cachedEntries = aggregates.values().stream()
                .map(value -> new LogicalInventoryEntry(value.representative, value.quantity, value.backingStackCount))
                .toList();
        cachedEntriesRevision = revision;
        return cachedEntries;
    }

    /** Aggregates only stacks at or beyond a compatibility index, used by backend-only browser views. */
    public List<LogicalInventoryEntry> entriesAtOrAfter(int minimumSlot) {
        Map<StackIdentity, Aggregate> aggregates = new LinkedHashMap<>();
        for (int index = Math.max(0, minimumSlot); index < backingStacks.size(); index++) {
            ItemStack stack = backingStacks.get(index);
            if (stack.isEmpty()) continue;
            StackIdentity key = new StackIdentity(stack);
            aggregates.computeIfAbsent(key, ignored -> new Aggregate(stack.copyWithCount(1))).add(stack.getCount());
        }
        return aggregates.values().stream()
                .map(value -> new LogicalInventoryEntry(value.representative, value.quantity, value.backingStackCount))
                .toList();
    }

    /** Returns defensive copies of the complete sparse compatibility-slot extent. */
    public List<ItemStack> backingStacks() {
        return backingStacks.stream().map(ItemStack::copy).toList();
    }

    /** Snapshot used to identify only the stacks received by a native container quick-move. */
    public List<ItemStack> visibleCompatibilitySnapshot() {
        ArrayList<ItemStack> snapshot = new ArrayList<>(36);
        for (int slot = 0; slot < 36; slot++) snapshot.add(syntheticStack(slot));
        return List.copyOf(snapshot);
    }

    /** Indexed synthetic-slot extent. It grows dynamically and has no fixed ceiling. */
    public int syntheticSlotCount() {
        return backingStacks.size();
    }

    /** Occupied slots plus one append slot, unless the JVM list index space itself is exhausted. */
    public int compatibilitySlotCount() {
        return backingStacks.size() == Integer.MAX_VALUE ? Integer.MAX_VALUE : backingStacks.size() + 1;
    }

    /** First empty compatibility slot below a caller-provided exclusive limit, or {@code -1}. */
    public int firstEmptySyntheticSlot(int limitExclusive) {
        int limit = Math.max(0, limitExclusive);
        for (int slot = 0; slot < Math.min(backingStacks.size(), limit); slot++) {
            if (backingStacks.get(slot) == ItemStack.EMPTY) return slot;
        }
        return backingStacks.size() < limit ? backingStacks.size() : -1;
    }

    public ItemStack syntheticStack(int slot) {
        return slot >= 0 && slot < backingStacks.size() ? backingStacks.get(slot).copy() : ItemStack.EMPTY;
    }

    /** Live reference used only by the isolated vanilla Inventory compatibility mixin. */
    public ItemStack vanillaStackReference(int slot) {
        return slot >= 0 && slot < backingStacks.size() ? backingStacks.get(slot) : ItemStack.EMPTY;
    }

    /** Live reference for vanilla systems that consume or damage a matching backend stack in place. */
    public ItemStack findLiveStackReference(int minimumSlot, Predicate<ItemStack> predicate) {
        Objects.requireNonNull(predicate);
        for (int slot = Math.max(0, minimumSlot); slot < backingStacks.size(); slot++) {
            ItemStack stack = backingStacks.get(slot);
            if (!stack.isEmpty() && predicate.test(stack)) return stack;
        }
        return ItemStack.EMPTY;
    }

    public ItemStack findVanillaStackReference(ItemStack prototype) {
        if (prototype.isEmpty()) return ItemStack.EMPTY;
        return backingStacks.stream().filter(stack -> ItemStack.isSameItemSameComponents(stack, prototype)).findFirst().orElse(ItemStack.EMPTY);
    }

    public int indexOf(ItemStack prototype) {
        if (prototype.isEmpty()) return -1;
        for (int i = 0; i < backingStacks.size(); i++) {
            ItemStack stack = backingStacks.get(i);
            if (stack.isEmpty()) continue;
            if (stack == prototype || ItemStack.isSameItemSameComponents(stack, prototype)) return i;
        }
        return -1;
    }

    /** True only for the isolated live objects exposed to vanilla compatibility code. */
    public boolean ownsReference(ItemStack candidate) {
        if (candidate == null || candidate.isEmpty()) return false;
        for (ItemStack stack : backingStacks) if (stack == candidate) return true;
        return false;
    }

    public void removeReference(ItemStack reference) {
        for (int i = 0; i < backingStacks.size(); i++) {
            if (backingStacks.get(i) != reference) continue;
            backingStacks.set(i, ItemStack.EMPTY);
            trimTrailingEmptySlots();
            recalculateCapacity();
            changed();
            return;
        }
    }

    public List<ItemStack> drain() {
        List<ItemStack> drained = backingStacks.stream().filter(stack -> !stack.isEmpty()).map(ItemStack::copy).toList();
        clear();
        return drained;
    }

    public void tick(Player player) {
        for (int i = 0; i < backingStacks.size(); i++) {
            ItemStack stack = backingStacks.get(i);
            if (stack.isEmpty()) continue;
            stack.inventoryTick(player.level(), player, i, i == player.getInventory().selected);
        }
        reconcileExternalMutations();
    }

    @Override
    public InsertionResult insert(ItemStack stack, boolean simulate) {
        return insertAtOrAfter(stack, 0, simulate);
    }

    /**
     * Inserts a container transfer into the main grid from left to right and top to bottom, then
     * the hotbar, then the unbounded backend. Existing compatible visible stacks are filled first.
     */
    public InsertionResult insertInPlayerTransferOrder(ItemStack stack, boolean simulate) {
        if (stack == null || stack.isEmpty() || stack.getCount() <= 0) {
            return new InsertionResult(stack == null ? 0 : Math.max(0, stack.getCount()), 0,
                    ItemStack.EMPTY, 0, InsertionRejection.INVALID_ITEM);
        }
        int requested = stack.getCount();
        long unitCost = CapacityCosts.unitCost(stack);
        long available = capacity() - usedCapacity;
        if (available < 0) return rejected(stack, requested, InsertionRejection.OVER_CAPACITY);
        int accepted = (int)Math.min(requested, Math.min(Integer.MAX_VALUE, available / unitCost));
        if (accepted <= 0) return rejected(stack, requested, InsertionRejection.GLOBAL_CAPACITY);
        if (!simulate) {
            addInPlayerTransferOrder(stack, accepted);
            usedCapacity = saturatedAdd(usedCapacity, CapacityCosts.cost(stack, accepted));
            changed();
        }
        ItemStack remainder = accepted == requested ? ItemStack.EMPTY : stack.copyWithCount(requested - accepted);
        InsertionRejection rejection = accepted == requested ? InsertionRejection.NONE : InsertionRejection.GLOBAL_CAPACITY;
        return new InsertionResult(requested, accepted, remainder, CapacityCosts.cost(stack, accepted), rejection);
    }

    /**
     * Inserts without touching compatibility slots below {@code minimumSlot}. This is used for
     * pickup-to-hotbar preferences and explicit backend stowing; capacity remains the only limit.
     */
    public InsertionResult insertAtOrAfter(ItemStack stack, int minimumSlot, boolean simulate) {
        if (stack == null || stack.isEmpty() || stack.getCount() <= 0) {
            return new InsertionResult(stack == null ? 0 : Math.max(0, stack.getCount()), 0, ItemStack.EMPTY, 0, InsertionRejection.INVALID_ITEM);
        }
        if (minimumSlot < 0 || minimumSlot == Integer.MAX_VALUE) {
            return rejected(stack, stack.getCount(), InsertionRejection.INVALID_ITEM);
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
            addLegalStacks(stack, accepted, minimumSlot);
            usedCapacity = saturatedAdd(usedCapacity, CapacityCosts.cost(stack, accepted));
            changed();
        }
        ItemStack remainder = accepted == requested ? ItemStack.EMPTY : stack.copyWithCount(requested - accepted);
        InsertionRejection rejection = accepted == requested ? InsertionRejection.NONE : InsertionRejection.GLOBAL_CAPACITY;
        return new InsertionResult(requested, accepted, remainder, CapacityCosts.cost(stack, accepted), rejection);
    }

    /** Moves a visible stack wholly behind the vanilla 36-slot window without changing ownership or capacity. */
    public boolean stowSyntheticSlot(int sourceSlot) {
        if (sourceSlot < 0 || sourceSlot >= Math.min(36, backingStacks.size())) return false;
        ItemStack source = backingStacks.get(sourceSlot);
        if (source.isEmpty()) return false;
        backingStacks.set(sourceSlot, ItemStack.EMPTY);
        addLegalStacks(source, source.getCount(), 36);
        recalculateCapacity();
        changed();
        return true;
    }

    /** Stows every occupied vanilla main-grid position while preserving all hotbar positions. */
    public boolean stowMainGrid() {
        boolean moved = false;
        ArrayList<ItemStack> values = new ArrayList<>();
        for (int slot = 9; slot < Math.min(36, backingStacks.size()); slot++) {
            ItemStack stack = backingStacks.get(slot);
            if (stack.isEmpty()) continue;
            values.add(stack);
            backingStacks.set(slot, ItemStack.EMPTY);
            moved = true;
        }
        if (!moved) return false;
        for (ItemStack stack : values) addLegalStacks(stack, stack.getCount(), 36);
        trimTrailingEmptySlots();
        recalculateCapacity();
        changed();
        return true;
    }

    /** Moves one matching backend stack into the first empty main-grid position, if both exist. */
    public boolean moveBackendStackToMain(ItemStack prototype) {
        if (prototype == null || prototype.isEmpty()) return false;
        int target = -1;
        for (int slot = 9; slot < 36; slot++) {
            if (syntheticStack(slot).isEmpty()) { target = slot; break; }
        }
        if (target < 0) return false;
        int source = -1;
        for (int slot = 36; slot < backingStacks.size(); slot++) {
            if (ItemStack.isSameItemSameComponents(backingStacks.get(slot), prototype)) { source = slot; break; }
        }
        if (source < 0) return false;
        ensureSyntheticSlot(target);
        backingStacks.set(target, backingStacks.get(source));
        backingStacks.set(source, ItemStack.EMPTY);
        trimTrailingEmptySlots();
        recalculateCapacity();
        changed();
        return true;
    }

    /**
     * Temporarily stages one backend stack in a visible player slot so the active menu can run its
     * own quick-move implementation. The displaced visible stack remains owned by this transaction.
     */
    public BackendQuickMoveStage beginBackendQuickMove(ItemStack prototype, int playerSlot) {
        if (prototype == null || prototype.isEmpty() || playerSlot < 0 || playerSlot >= 36) return null;
        int sourceSlot = -1;
        for (int slot = 36; slot < backingStacks.size(); slot++) {
            if (ItemStack.isSameItemSameComponents(backingStacks.get(slot), prototype)) {
                sourceSlot = slot;
                break;
            }
        }
        if (sourceSlot < 0) return null;

        ensureSyntheticSlot(playerSlot);
        ItemStack staged = backingStacks.get(sourceSlot);
        ItemStack displaced = backingStacks.get(playerSlot);
        int stagedCount = staged.getCount();
        backingStacks.set(playerSlot, staged);
        backingStacks.set(sourceSlot, ItemStack.EMPTY);
        recalculateCapacity();
        changed();
        return new BackendQuickMoveStage(playerSlot, sourceSlot, displaced, staged.copyWithCount(1), stagedCount);
    }

    /** Restores the staged player slot and returns the amount accepted by the active menu. */
    public int finishBackendQuickMove(BackendQuickMoveStage stage) {
        if (stage == null || stage.finished) return 0;
        stage.finished = true;
        ItemStack remainder = stage.playerSlot < backingStacks.size()
                ? backingStacks.get(stage.playerSlot) : ItemStack.EMPTY;
        int remaining = ItemStack.isSameItemSameComponents(remainder, stage.prototype)
                ? Math.min(stage.stagedCount, remainder.getCount()) : 0;

        ensureSyntheticSlot(stage.sourceSlot);
        backingStacks.set(stage.sourceSlot, remainder.isEmpty() ? ItemStack.EMPTY : remainder);
        ensureSyntheticSlot(stage.playerSlot);
        backingStacks.set(stage.playerSlot, stage.displaced.isEmpty() ? ItemStack.EMPTY : stage.displaced);
        trimTrailingEmptySlots();
        recalculateCapacity();
        changed();
        return stage.stagedCount - remaining;
    }

    /**
     * Repositions only the positive player-slot deltas produced by a custom menu quick-move.
     * Existing visible stacks and their placement remain untouched.
     */
    public boolean relocateReceivedVisibleStacks(List<ItemStack> before, boolean backendOnly) {
        if (before == null || before.size() < 36) return false;
        ArrayList<ReceivedStack> received = new ArrayList<>();
        for (int slot = 0; slot < 36; slot++) {
            ItemStack previous = before.get(slot);
            ItemStack current = syntheticStack(slot);
            int amount = previous.isEmpty() ? current.getCount()
                    : ItemStack.isSameItemSameComponents(previous, current)
                            ? Math.max(0, current.getCount() - previous.getCount()) : 0;
            if (amount > 0) received.add(new ReceivedStack(slot, current.copyWithCount(amount)));
        }
        if (received.isEmpty()) return false;

        ArrayList<ItemStack> extracted = new ArrayList<>(received.size());
        for (ReceivedStack value : received) {
            ItemStack moved = extractSyntheticSlot(value.slot, value.stack.getCount(), false);
            if (!moved.isEmpty()) extracted.add(moved);
        }
        for (ItemStack moved : extracted) {
            InsertionResult insertion = backendOnly
                    ? insertAtOrAfter(moved, 36, false)
                    : insertInPlayerTransferOrder(moved, false);
            if (!insertion.acceptedAll()) {
                insertAtOrAfter(insertion.remainder(), 36, false);
            }
        }
        return !extracted.isEmpty();
    }

    /** Atomically swaps two compatibility positions. Used only by an explicit hotbar-cycle key press. */
    public void swapSyntheticSlots(int first, int second) {
        if (first < 0 || second < 0 || first == Integer.MAX_VALUE || second == Integer.MAX_VALUE) {
            throw new IndexOutOfBoundsException("Synthetic slot swap " + first + " <-> " + second);
        }
        if (first == second) return;
        ensureSyntheticSlot(Math.max(first, second));
        ItemStack value = backingStacks.get(first);
        backingStacks.set(first, backingStacks.get(second));
        backingStacks.set(second, value);
        trimTrailingEmptySlots();
        recalculateCapacity();
        changed();
    }

    /**
     * Applies a user-requested category/sort view once. Hotbar slots 0-8 are retained verbatim,
     * the requested distinct representatives fill slots 9-35, and everything else remains owned
     * in dynamically growing backend slots.
     */
    public void arrangeMainGrid(List<Integer> preferredBackingIndexes) {
        ArrayList<ItemStack> original = new ArrayList<>(backingStacks);
        boolean[] consumed = new boolean[original.size()];
        ArrayList<ItemStack> arranged = new ArrayList<>(Math.max(36, original.size()));

        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = slot < original.size() ? original.get(slot) : ItemStack.EMPTY;
            arranged.add(stack);
            if (slot < consumed.length && !stack.isEmpty()) consumed[slot] = true;
        }

        int visible = 0;
        for (int index : preferredBackingIndexes) {
            if (visible >= 27) break;
            if (index < 9 || index >= original.size() || consumed[index]) continue;
            ItemStack stack = original.get(index);
            if (stack.isEmpty()) continue;
            arranged.add(stack);
            consumed[index] = true;
            visible++;
        }
        while (arranged.size() < 36) arranged.add(ItemStack.EMPTY);

        for (int index = 9; index < original.size(); index++) {
            ItemStack stack = original.get(index);
            if (!consumed[index] && !stack.isEmpty()) arranged.add(stack);
        }

        backingStacks.clear();
        backingStacks.addAll(arranged);
        trimTrailingEmptySlots();
        recalculateCapacity();
        changed();
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

    /** Inserts only into the requested compatibility slot, preserving explicit API and GUI placement. */
    public InsertionResult insertIntoSyntheticSlot(ItemStack stack, int slot, boolean simulate) {
        if (stack == null || stack.isEmpty() || stack.getCount() <= 0 || slot < 0) {
            return rejected(stack == null ? ItemStack.EMPTY : stack, stack == null ? 0 : Math.max(0, stack.getCount()),
                    InsertionRejection.INVALID_ITEM);
        }
        ItemStack existing = slot < backingStacks.size() ? backingStacks.get(slot) : ItemStack.EMPTY;
        if (!existing.isEmpty() && !ItemStack.isSameItemSameComponents(existing, stack)) {
            return rejected(stack, stack.getCount(), InsertionRejection.INVALID_ITEM);
        }
        int stackSpace = existing.isEmpty()
                ? stack.getMaxStackSize()
                : existing.getMaxStackSize() - existing.getCount();
        long available = capacity() - usedCapacity;
        if (available < 0) return rejected(stack, stack.getCount(), InsertionRejection.OVER_CAPACITY);
        int accepted = (int)Math.min(stack.getCount(), Math.min(stackSpace, available / CapacityCosts.unitCost(stack)));
        if (accepted <= 0) return rejected(stack, stack.getCount(), InsertionRejection.GLOBAL_CAPACITY);
        if (!simulate) {
            ensureSyntheticSlot(slot);
            if (existing.isEmpty()) backingStacks.set(slot, stack.copyWithCount(accepted));
            else existing.grow(accepted);
            usedCapacity = saturatedAdd(usedCapacity, CapacityCosts.cost(stack, accepted));
            changed();
        }
        ItemStack remainder = accepted == stack.getCount() ? ItemStack.EMPTY : stack.copyWithCount(stack.getCount() - accepted);
        InsertionRejection rejection = accepted == stack.getCount() ? InsertionRejection.NONE : InsertionRejection.GLOBAL_CAPACITY;
        return new InsertionResult(stack.getCount(), accepted, remainder, CapacityCosts.cost(stack, accepted), rejection);
    }

    @Override
    public ExtractionResult extract(ItemStack prototype, int amount, boolean simulate) {
        return extractAtOrAfter(prototype, amount, 0, simulate);
    }

    /** Extracts a matching identity without touching compatibility positions below {@code minimumSlot}. */
    public ExtractionResult extractAtOrAfter(ItemStack prototype, int amount, int minimumSlot, boolean simulate) {
        if (prototype == null || prototype.isEmpty() || amount <= 0) return new ExtractionResult(Math.max(0, amount), 0, List.of());
        int remaining = amount;
        ArrayList<ItemStack> result = new ArrayList<>();
        for (int index = Math.max(0, minimumSlot); index < backingStacks.size() && remaining > 0; index++) {
            ItemStack stored = backingStacks.get(index);
            if (!ItemStack.isSameItemSameComponents(stored, prototype)) continue;
            int taken = Math.min(stored.getCount(), remaining);
            ItemStack extractedStack = stored.copyWithCount(taken);
            result.add(extractedStack);
            remaining -= taken;
            if (!simulate) {
                stored.shrink(taken);
                usedCapacity = Math.max(0, usedCapacity - CapacityCosts.cost(extractedStack, taken));
                if (stored.isEmpty()) backingStacks.set(index, ItemStack.EMPTY);
            }
        }
        int extracted = amount - remaining;
        if (!simulate && extracted > 0) {
            trimTrailingEmptySlots();
            changed();
        }
        return new ExtractionResult(amount, extracted, result);
    }

    public ItemStack extractSyntheticSlot(int slot, int amount, boolean simulate) {
        if (slot < 0 || slot >= backingStacks.size() || amount <= 0) return ItemStack.EMPTY;
        ItemStack stored = backingStacks.get(slot);
        if (stored.isEmpty()) return ItemStack.EMPTY;
        int taken = Math.min(amount, stored.getCount());
        ItemStack result = stored.copyWithCount(taken);
        if (!simulate) {
            stored.shrink(taken);
            usedCapacity = Math.max(0, usedCapacity - CapacityCosts.cost(result, taken));
            if (stored.isEmpty()) backingStacks.set(slot, ItemStack.EMPTY);
            trimTrailingEmptySlots();
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
        if (slot < 0) throw new IndexOutOfBoundsException("Synthetic slot " + slot);
        if (replacement == null) replacement = ItemStack.EMPTY;
        if (replacement.isEmpty() && slot >= backingStacks.size()) return;
        ItemStack previous = slot < backingStacks.size() ? backingStacks.get(slot) : ItemStack.EMPTY;
        long actualUsedCapacity = calculateUsedCapacity();
        long previousCost = previous.isEmpty() ? 0 : CapacityCosts.cost(previous, previous.getCount());
        long replacementCost = replacement.isEmpty() ? 0 : CapacityCosts.cost(replacement, replacement.getCount());
        long projected = saturatedAdd(Math.max(0, actualUsedCapacity - previousCost), replacementCost);
        if (replacement.getCount() > replacement.getMaxStackSize()) throw new IllegalArgumentException("Synthetic slots must contain legal ItemStacks");
        if (enforceCapacityIncrease && projected > capacity() && projected > actualUsedCapacity) {
            throw new IllegalArgumentException("Replacement exceeds inventory capacity");
        }
        ensureSyntheticSlot(slot);
        backingStacks.set(slot, replacement.isEmpty() ? ItemStack.EMPTY : replacement.copy());
        trimTrailingEmptySlots();
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
            addLegalStacks(stack, stack.getCount(), 0);
            imported = true;
        }
        if (imported) {
            recalculateCapacity();
            changed();
        }
    }

    /** Detects item-use and durability mutations made through live vanilla stack references. */
    public void reconcileExternalMutations() {
        boolean normalized = false;
        for (int i = 0; i < backingStacks.size(); i++) {
            ItemStack stack = backingStacks.get(i);
            if (stack.isEmpty()) {
                if (stack != ItemStack.EMPTY) {
                    backingStacks.set(i, ItemStack.EMPTY);
                    normalized = true;
                }
                continue;
            }
            if (stack.getCount() <= stack.getMaxStackSize()) continue;
            int excess = stack.getCount() - stack.getMaxStackSize();
            stack.setCount(stack.getMaxStackSize());
            addLegalStacks(stack, excess, 0);
            normalized = true;
        }
        normalized |= trimTrailingEmptySlots();
        long currentHash = calculateHash();
        long calculatedCapacity = calculateUsedCapacity();
        boolean accountingMismatch = calculatedCapacity != usedCapacity;
        if (normalized || currentHash != observedHash || accountingMismatch) {
            if (accountingMismatch && !normalized && currentHash == observedHash) {
                StacksNotSlots.LOGGER.warn("Self-repaired inventory capacity accounting from {} to {}", usedCapacity, calculatedCapacity);
            }
            usedCapacity = calculatedCapacity;
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
            if (stack.isEmpty()) continue;
            if (stack.getCount() <= 0) {
                errors.add("Backing index " + index + " is non-positive");
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
        for (ItemStack stack : stacks) backingStacks.add(stack.isEmpty() ? ItemStack.EMPTY : stack.copy());
        trimTrailingEmptySlots();
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
        trimTrailingEmptySlots();
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
        for (int slot = 0; slot < backingStacks.size(); slot++) {
            ItemStack stack = backingStacks.get(slot);
            if (stack.isEmpty()) continue;
            CompoundTag encoded = (CompoundTag)stack.save(provider, new CompoundTag());
            encoded.putInt("SNS_Slot", slot);
            items.add(encoded);
        }
        for (int i = 0; i < unresolvedEntries.size(); i++) items.add(unresolvedEntries.getCompound(i).copy());
        root.put("Items", items);
        root.putLong("Revision", revision);
        return root;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag root) {
        backingStacks.clear();
        unresolvedEntries = new ListTag();
        int version = root.getInt("Version");
        ListTag items = root.getList("Items", CompoundTag.TAG_COMPOUND);
        for (int i = 0; i < items.size(); i++) {
            CompoundTag encoded = items.getCompound(i);
            int savedSlot = version >= 2 && encoded.contains("SNS_Slot", CompoundTag.TAG_INT)
                    ? encoded.getInt("SNS_Slot") : -1;
            ItemStack.parse(provider, encoded).ifPresentOrElse(stack -> {
                if (savedSlot >= 0) restoreAt(savedSlot, stack);
                else addDeserialized(stack);
            }, () -> {
                unresolvedEntries.add(encoded.copy());
                StacksNotSlots.LOGGER.warn("Preserving unresolved inventory entry during load: {}", encoded);
            });
        }
        revision = Math.max(0, root.getLong("Revision"));
        recalculateCapacity();
        trimTrailingEmptySlots();
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

    private void restoreAt(int slot, ItemStack stack) {
        if (stack.isEmpty()) return;
        int remaining = stack.getCount();
        int target = slot;
        while (remaining > 0) {
            ensureSyntheticSlot(target);
            int amount = Math.min(remaining, stack.getMaxStackSize());
            backingStacks.set(target++, stack.copyWithCount(amount));
            remaining -= amount;
        }
    }

    private void addLegalStacks(ItemStack incoming, int amount, int minimumSlot) {
        int remaining = amount;
        if (incoming.isStackable()) {
            for (int slot = minimumSlot; slot < backingStacks.size(); slot++) {
                if (remaining == 0) break;
                ItemStack stored = backingStacks.get(slot);
                if (stored.isEmpty()) continue;
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
            int vacant = firstCanonicalEmptySlot(minimumSlot);
            if (vacant < 0 && backingStacks.size() < minimumSlot) {
                ensureSyntheticSlot(minimumSlot);
                vacant = minimumSlot;
            }
            if (vacant < 0) backingStacks.add(incoming.copyWithCount(moved));
            else backingStacks.set(vacant, incoming.copyWithCount(moved));
            remaining -= moved;
        }
    }

    private void addInPlayerTransferOrder(ItemStack incoming, int amount) {
        int remaining = amount;
        if (incoming.isStackable()) {
            for (int ordinal = 0; ordinal < 36 && remaining > 0; ordinal++) {
                int slot = playerTransferSlot(ordinal);
                if (slot >= backingStacks.size()) continue;
                ItemStack stored = backingStacks.get(slot);
                if (stored.isEmpty() || !ItemStack.isSameItemSameComponents(stored, incoming)) continue;
                int moved = Math.min(remaining, stored.getMaxStackSize() - stored.getCount());
                if (moved > 0) {
                    stored.grow(moved);
                    remaining -= moved;
                }
            }
        }
        for (int ordinal = 0; ordinal < 36 && remaining > 0; ordinal++) {
            int slot = playerTransferSlot(ordinal);
            if (slot < backingStacks.size() && !backingStacks.get(slot).isEmpty()) continue;
            ensureSyntheticSlot(slot);
            int moved = Math.min(remaining, incoming.getMaxStackSize());
            backingStacks.set(slot, incoming.copyWithCount(moved));
            remaining -= moved;
        }
        if (remaining > 0) addLegalStacks(incoming, remaining, 36);
    }

    private static int playerTransferSlot(int ordinal) {
        return ordinal < 27 ? ordinal + 9 : ordinal - 27;
    }

    /** Avoids reusing a live reference that vanilla has temporarily shrunk to zero mid-transaction. */
    private int firstCanonicalEmptySlot(int minimumSlot) {
        for (int slot = minimumSlot; slot < backingStacks.size(); slot++) {
            if (backingStacks.get(slot) == ItemStack.EMPTY) return slot;
        }
        return -1;
    }

    private void ensureSyntheticSlot(int slot) {
        if (slot < 0 || slot == Integer.MAX_VALUE) throw new IndexOutOfBoundsException("Synthetic slot " + slot);
        backingStacks.ensureCapacity(slot + 1);
        while (backingStacks.size() <= slot) backingStacks.add(ItemStack.EMPTY);
    }

    private boolean trimTrailingEmptySlots() {
        int previousSize = backingStacks.size();
        while (!backingStacks.isEmpty() && backingStacks.getLast().isEmpty()) backingStacks.removeLast();
        return backingStacks.size() != previousSize;
    }

    private void recalculateCapacity() {
        usedCapacity = calculateUsedCapacity();
    }

    private long calculateUsedCapacity() {
        long calculated = 0;
        for (ItemStack stack : backingStacks) {
            if (stack.isEmpty()) continue;
            calculated = saturatedAdd(calculated, CapacityCosts.cost(stack, stack.getCount()));
        }
        return calculated;
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

    public static final class BackendQuickMoveStage {
        private final int playerSlot;
        private final int sourceSlot;
        private final ItemStack displaced;
        private final ItemStack prototype;
        private final int stagedCount;
        private boolean finished;

        private BackendQuickMoveStage(int playerSlot, int sourceSlot, ItemStack displaced,
                                      ItemStack prototype, int stagedCount) {
            this.playerSlot = playerSlot;
            this.sourceSlot = sourceSlot;
            this.displaced = displaced;
            this.prototype = prototype;
            this.stagedCount = stagedCount;
        }
    }

    private record ReceivedStack(int slot, ItemStack stack) {}

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
