package com.cappleapple.stacksnotslots.api.compat;

import com.cappleapple.stacksnotslots.api.inventory.DynamicCapacityInventory;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * Keeps vanilla's public 36-entry {@link Inventory#items} list usable by mods which access the
 * field directly instead of calling {@link Inventory#getItem(int)}. The list is only a live view;
 * it never defines capacity or the size of the logical inventory.
 */
public final class VanillaInventoryMirror {
    private VanillaInventoryMirror() {}

    /** Publishes the live first-36 references while leaving the unbounded backend untouched. */
    public static void publish(DynamicCapacityInventory inventory, List<ItemStack> vanillaItems) {
        int visibleSlots = Math.min(Inventory.INVENTORY_SIZE, vanillaItems.size());
        for (int slot = 0; slot < visibleSlots; slot++) {
            ItemStack live = inventory.vanillaStackReference(slot);
            if (vanillaItems.get(slot) != live) vanillaItems.set(slot, live);
        }
    }

    /**
     * Imports direct list replacements before republishing live references. Copies are captured
     * first because each inventory mutation can immediately refresh the public mirror.
     */
    public static void reconcileDirectWrites(DynamicCapacityInventory inventory, List<ItemStack> vanillaItems) {
        int visibleSlots = Math.min(Inventory.INVENTORY_SIZE, vanillaItems.size());
        ArrayList<Replacement> replacements = new ArrayList<>();
        for (int slot = 0; slot < visibleSlots; slot++) {
            ItemStack exposed = vanillaItems.get(slot);
            if (exposed != inventory.vanillaStackReference(slot)) {
                replacements.add(new Replacement(slot, exposed.copy()));
            }
        }
        for (Replacement replacement : replacements) {
            inventory.replaceSyntheticSlotFromItemUse(replacement.slot(), replacement.stack());
        }
        publish(inventory, vanillaItems);
    }

    private record Replacement(int slot, ItemStack stack) {}
}
