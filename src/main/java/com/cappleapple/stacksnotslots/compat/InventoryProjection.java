package com.cappleapple.stacksnotslots.compat;

import com.cappleapple.stacksnotslots.category.CategoryDefinition;
import com.cappleapple.stacksnotslots.category.CategoryMatcher;
import com.cappleapple.stacksnotslots.category.SortMode;
import com.cappleapple.stacksnotslots.data.PlayerInventoryData;
import com.cappleapple.stacksnotslots.hotbar.BindingType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Locale;
import java.util.Objects;
import java.util.HashSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/** Builds the stable 36-slot vanilla view over dynamic storage. */
public final class InventoryProjection {
    private InventoryProjection() {}

    public static int[] build(PlayerInventoryData data) {
        int[] mapping = new int[Inventory.INVENTORY_SIZE];
        for (int slot = 0; slot < mapping.length; slot++) mapping[slot] = slot;

        HashSet<Integer> reserved = new HashSet<>();
        for (int slot = 0; slot < 9; slot++) {
            if (data.hotbar().get(slot).type() != BindingType.EMPTY) {
                mapping[slot] = data.hotbar().resolveIndex(slot, data.inventory(), data.categories());
            }
            int index = mapping[slot];
            if (index >= 0 && !data.inventory().syntheticStack(index).isEmpty()) reserved.add(index);
        }

        if (data.selectedCategoryPreference() == null) return mapping;
        CategoryDefinition category = data.categories().find(data.selectedCategoryPreference());
        if (category == null || !category.enabled()) return mapping;

        Arrays.fill(mapping, 9, mapping.length, -1);
        ArrayList<ProjectedStack> candidates = new ArrayList<>();
        for (int index = 0; index < data.inventory().syntheticSlotCount(); index++) {
            ItemStack stack = data.inventory().syntheticStack(index);
            if (stack.isEmpty() || reserved.contains(index) || !CategoryMatcher.matches(category, stack)) continue;
            candidates.add(new ProjectedStack(index, stack));
        }
        candidates.sort(comparator(data.inventorySortPreference()));
        for (int offset = 0; offset < Math.min(27, candidates.size()); offset++) {
            mapping[9 + offset] = candidates.get(offset).index();
        }
        return mapping;
    }

    public static int stateHash(PlayerInventoryData data) {
        int hash = Objects.hash(data.selectedCategoryPreference(), data.inventorySortPreference(), data.categories().revision());
        for (int slot = 0; slot < 9; slot++) hash = 31 * hash + data.hotbar().get(slot).hashCode();
        return hash;
    }

    private static Comparator<ProjectedStack> comparator(SortMode mode) {
        Comparator<ProjectedStack> byName = Comparator.comparing(
                value -> value.stack().getHoverName().getString().toLowerCase(Locale.ROOT));
        Comparator<ProjectedStack> byQuantity = Comparator.comparingInt(value -> value.stack().getCount());
        Comparator<ProjectedStack> byId = Comparator.comparing(
                value -> BuiltInRegistries.ITEM.getKey(value.stack().getItem()).toString());
        Comparator<ProjectedStack> selected = switch (mode) {
            case NAME_ASCENDING -> byName;
            case NAME_DESCENDING -> byName.reversed();
            case QUANTITY_ASCENDING -> byQuantity;
            case QUANTITY_DESCENDING -> byQuantity.reversed();
            case REGISTRY_ID -> byId;
            case MOD_NAMESPACE -> Comparator.comparing(
                    value -> BuiltInRegistries.ITEM.getKey(value.stack().getItem()).getNamespace());
        };
        return selected.thenComparing(byId).thenComparingInt(ProjectedStack::index);
    }

    private record ProjectedStack(int index, ItemStack stack) {}
}
