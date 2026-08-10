package com.cappleapple.stacksnotslots.api.inventory;

import java.util.List;
import net.minecraft.world.item.ItemStack;

/** Defensive server/client synchronization snapshot for a complete inventory revision. */
public record InventorySnapshot(long revision, List<ItemStack> stacks) {
    public InventorySnapshot {
        revision = Math.max(0, revision);
        stacks = stacks == null ? List.of() : stacks.stream()
                .map(stack -> stack == null || stack.isEmpty() ? ItemStack.EMPTY : stack.copy())
                .toList();
    }

    @Override public List<ItemStack> stacks() {
        return stacks.stream().map(stack -> stack.isEmpty() ? ItemStack.EMPTY : stack.copy()).toList();
    }
}
