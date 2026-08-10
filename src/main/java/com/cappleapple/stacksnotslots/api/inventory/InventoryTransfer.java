package com.cappleapple.stacksnotslots.api.inventory;

import com.cappleapple.stacksnotslots.api.ExtractionResult;
import com.cappleapple.stacksnotslots.api.ICapacityInventory;
import com.cappleapple.stacksnotslots.api.InsertionResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.minecraft.world.item.ItemStack;

/** Simulation-first moves between any two compatible capacity inventories on their owning thread. */
public final class InventoryTransfer {
    private InventoryTransfer() { }

    public static Result move(ICapacityInventory source, ICapacityInventory destination,
                              ItemStack prototype, int requested, boolean simulate) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(destination, "destination");
        if (source == destination || prototype == null || prototype.isEmpty() || requested <= 0) {
            return new Result(Math.max(0, requested), 0, List.of());
        }
        ExtractionResult available = source.extract(prototype, requested, true);
        int accepted = 0;
        for (ItemStack stack : available.extractedStacks()) {
            InsertionResult proposal = destination.insert(stack, true);
            accepted += proposal.acceptedAmount();
            if (proposal.acceptedAmount() < stack.getCount()) break;
        }
        if (simulate || accepted == 0) return new Result(requested, accepted, List.of());
        ExtractionResult extracted = source.extract(prototype, accepted, false);
        int committed = 0;
        ArrayList<ItemStack> unresolved = new ArrayList<>();
        for (ItemStack stack : extracted.extractedStacks()) {
            InsertionResult insertion = destination.insert(stack, false);
            committed += insertion.acceptedAmount();
            if (!insertion.remainder().isEmpty()) {
                InsertionResult restored = source.insert(insertion.remainder(), false);
                if (!restored.remainder().isEmpty()) unresolved.add(restored.remainder());
            }
        }
        return new Result(requested, committed, unresolved);
    }

    /**
     * Unexpected commit-time remainders are returned to the caller if the source's insertion policy
     * also changed after simulation. The owner must retain, drop, or otherwise resolve those stacks.
     */
    public record Result(int requested, int moved, List<ItemStack> unresolvedRemainders) {
        public Result {
            unresolvedRemainders = unresolvedRemainders.stream().map(ItemStack::copy).toList();
        }

        @Override public List<ItemStack> unresolvedRemainders() {
            return unresolvedRemainders.stream().map(ItemStack::copy).toList();
        }

        public boolean movedAll() { return moved == requested && unresolvedRemainders.isEmpty(); }
    }
}
