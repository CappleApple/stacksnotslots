package com.cappleapple.stacksnotslots.inventory;

import com.cappleapple.stacksnotslots.api.CapacityAmount;
import com.cappleapple.stacksnotslots.api.CapacityCostProvider;
import java.util.Comparator;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

public final class CapacityCosts {
    public static final long STACK_EQUIVALENT_UNITS = 64L;
    public static final int DEFAULT_INVENTORY_CAPACITY_UNITS = Inventory.INVENTORY_SIZE * (int) STACK_EQUIVALENT_UNITS;
    private static final CopyOnWriteArrayList<Registration> PROVIDERS = new CopyOnWriteArrayList<>();

    private CapacityCosts() {}

    /** Legacy whole-unit view. Fractional costs are rounded upward; use {@link #unitCostExact(ItemStack)} internally. */
    public static long unitCost(ItemStack stack) {
        return unitCostExact(stack).ceilToLong();
    }

    public static CapacityAmount unitCostExact(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return CapacityAmount.ZERO;
        for (Registration registration : PROVIDERS) {
            long cost = registration.provider.capacityCostPerItem(stack);
            if (cost >= 0) return CapacityAmount.of(Math.max(1, cost));
        }
        int maxStackSize = Math.max(1, stack.getMaxStackSize());
        return CapacityAmount.fraction(STACK_EQUIVALENT_UNITS, maxStackSize);
    }

    /** Legacy whole-unit view of the total, rounded upward for compatibility. */
    public static long cost(ItemStack stack, long amount) {
        return costExact(stack, amount).ceilToLong();
    }

    public static CapacityAmount costExact(ItemStack stack, long amount) {
        if (stack == null || stack.isEmpty() || amount <= 0) return CapacityAmount.ZERO;
        return unitCostExact(stack).multiply(amount);
    }

    public static AutoCloseable register(ResourceLocation id, int priority, CapacityCostProvider provider) {
        Registration registration = new Registration(Objects.requireNonNull(id), priority, Objects.requireNonNull(provider));
        if (PROVIDERS.stream().anyMatch(existing -> existing.id.equals(id))) {
            throw new IllegalArgumentException("Capacity cost provider already registered: " + id);
        }
        PROVIDERS.add(registration);
        PROVIDERS.sort(Comparator.comparingInt(Registration::priority).reversed().thenComparing(r -> r.id.toString()));
        return () -> PROVIDERS.remove(registration);
    }

    private record Registration(ResourceLocation id, int priority, CapacityCostProvider provider) {}
}
