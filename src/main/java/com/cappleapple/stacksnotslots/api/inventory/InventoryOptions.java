package com.cappleapple.stacksnotslots.api.inventory;

import java.util.Objects;
import java.util.function.LongSupplier;
import java.util.function.Predicate;
import net.minecraft.world.item.ItemStack;

/** Immutable creation options for a capacity inventory. */
public record InventoryOptions(
        LongSupplier capacity,
        Predicate<ItemStack> insertionRule,
        Predicate<ItemStack> extractionRule,
        Runnable mutationListener) {

    public InventoryOptions {
        Objects.requireNonNull(capacity, "capacity");
        Objects.requireNonNull(insertionRule, "insertionRule");
        Objects.requireNonNull(extractionRule, "extractionRule");
        Objects.requireNonNull(mutationListener, "mutationListener");
    }

    public static Builder builder(LongSupplier capacity) { return new Builder(capacity); }
    public static InventoryOptions fixed(long capacity) { return builder(() -> capacity).build(); }

    public static final class Builder {
        private final LongSupplier capacity;
        private Predicate<ItemStack> insertionRule = stack -> true;
        private Predicate<ItemStack> extractionRule = stack -> true;
        private Runnable mutationListener = () -> { };

        private Builder(LongSupplier capacity) { this.capacity = Objects.requireNonNull(capacity); }
        public Builder insertionRule(Predicate<ItemStack> value) { insertionRule = Objects.requireNonNull(value); return this; }
        public Builder extractionRule(Predicate<ItemStack> value) { extractionRule = Objects.requireNonNull(value); return this; }
        public Builder mutationListener(Runnable value) { mutationListener = Objects.requireNonNull(value); return this; }
        public InventoryOptions build() { return new InventoryOptions(capacity, insertionRule, extractionRule, mutationListener); }
    }
}
