package com.cappleapple.stacksnotslots.inventory;

import com.cappleapple.stacksnotslots.api.CapacityCostProvider;
import java.util.Comparator;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public final class CapacityCosts {
    public static final long STACK_EQUIVALENT_UNITS = 64L;
    private static final CopyOnWriteArrayList<Registration> PROVIDERS = new CopyOnWriteArrayList<>();

    private CapacityCosts() {}

    public static long unitCost(ItemStack stack) {
        if (stack.isEmpty()) return 0;
        for (Registration registration : PROVIDERS) {
            long cost = registration.provider.capacityCostPerItem(stack);
            if (cost >= 0) return Math.max(1, cost);
        }
        int maxStackSize = Math.max(1, stack.getMaxStackSize());
        return Math.max(1, Math.ceilDiv(STACK_EQUIVALENT_UNITS, maxStackSize));
    }

    public static long cost(ItemStack stack, long amount) {
        if (stack.isEmpty() || amount <= 0) return 0;
        try {
            return Math.multiplyExact(unitCost(stack), amount);
        } catch (ArithmeticException ignored) {
            return Long.MAX_VALUE;
        }
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
