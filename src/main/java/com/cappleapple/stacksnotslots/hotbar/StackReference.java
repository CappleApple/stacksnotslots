package com.cappleapple.stacksnotslots.hotbar;

import com.cappleapple.stacksnotslots.StacksNotSlots;
import java.util.Optional;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

/**
 * A count-free stack identity descriptor. It preserves components needed to select an entry but never
 * represents owned quantity; ownership remains exclusively in the capacity inventory.
 */
public final class StackReference {
    private final ItemStack prototype;
    private final CompoundTag unresolved;

    private StackReference(ItemStack prototype) {
        this.prototype = prototype.copyWithCount(1);
        this.unresolved = null;
    }

    private StackReference(CompoundTag unresolved) {
        this.prototype = ItemStack.EMPTY;
        this.unresolved = unresolved.copy();
    }

    public static StackReference of(ItemStack stack) {
        if (stack.isEmpty()) throw new IllegalArgumentException("A stack reference cannot be empty");
        return new StackReference(stack);
    }

    public boolean matches(ItemStack stack) {
        return !prototype.isEmpty() && ItemStack.isSameItemSameComponents(prototype, stack);
    }

    public Tag save(HolderLookup.Provider provider) {
        return unresolved == null ? prototype.save(provider, new CompoundTag()) : unresolved.copy();
    }

    public static Optional<StackReference> load(HolderLookup.Provider provider, CompoundTag tag) {
        Optional<ItemStack> parsed = ItemStack.parse(provider, tag).filter(stack -> !stack.isEmpty());
        if (parsed.isPresent()) return parsed.map(StackReference::new);
        StacksNotSlots.LOGGER.warn("Preserving unresolved hotbar stack reference: {}", tag);
        return Optional.of(new StackReference(tag));
    }
}
