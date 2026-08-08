package com.cappleapple.stacksnotslots.compat;

import com.cappleapple.stacksnotslots.category.CategoryDefinition;
import com.cappleapple.stacksnotslots.category.CategoryMatcher;
import com.cappleapple.stacksnotslots.category.SortMode;
import com.cappleapple.stacksnotslots.data.PlayerInventoryData;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/** Explicit, one-shot category/sort arrangement for vanilla's main 27-slot grid. */
public final class InventoryProjection {
    private InventoryProjection() {}

    /** Normal rendering is always a direct compatibility-slot view and never continuously re-sorts. */
    public static int[] build(PlayerInventoryData data) {
        int[] mapping = new int[Inventory.INVENTORY_SIZE];
        for (int slot = 0; slot < mapping.length; slot++) mapping[slot] = slot;
        return mapping;
    }

    /**
     * Rebuilds the main grid only in response to the player's category or sort click. Hotbar positions
     * remain untouched and only one legal stack per distinct item/component identity is shown.
     */
    public static void applyExplicitView(PlayerInventoryData data) {
        CategoryDefinition category = data.selectedCategoryPreference() == null
                ? null : data.categories().find(data.selectedCategoryPreference());
        if (category != null && !category.enabled()) category = null;

        List<ItemStack> stacks = data.inventory().backingStacks();
        LinkedHashMap<StackIdentity, ProjectedStack> distinct = new LinkedHashMap<>();
        for (int index = 9; index < stacks.size(); index++) {
            ItemStack stack = stacks.get(index);
            if (stack.isEmpty() || category != null && !CategoryMatcher.matches(category, stack)) continue;
            StackIdentity identity = new StackIdentity(stack);
            ProjectedStack existing = distinct.get(identity);
            if (existing == null) distinct.put(identity, new ProjectedStack(index, stack, stack.getCount()));
            else distinct.put(identity, existing.withAdditionalQuantity(stack.getCount()));
        }

        ArrayList<ProjectedStack> ordered = new ArrayList<>(distinct.values());
        ordered.sort(comparator(data.inventorySortPreference()));
        data.inventory().arrangeMainGrid(ordered.stream().map(ProjectedStack::index).toList());
    }

    private static Comparator<ProjectedStack> comparator(SortMode mode) {
        Comparator<ProjectedStack> byName = Comparator.comparing(
                value -> value.stack().getHoverName().getString().toLowerCase(Locale.ROOT));
        Comparator<ProjectedStack> byQuantity = Comparator.comparingLong(ProjectedStack::quantity);
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

    private record ProjectedStack(int index, ItemStack stack, long quantity) {
        private ProjectedStack withAdditionalQuantity(int amount) {
            return new ProjectedStack(index, stack, quantity + amount);
        }
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
