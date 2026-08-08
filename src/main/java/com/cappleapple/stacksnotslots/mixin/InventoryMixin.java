package com.cappleapple.stacksnotslots.mixin;

import com.cappleapple.stacksnotslots.api.InsertionResult;
import com.cappleapple.stacksnotslots.data.ModAttachments;
import com.cappleapple.stacksnotslots.data.PlayerInventoryData;
import com.cappleapple.stacksnotslots.hotbar.BindingType;
import com.cappleapple.stacksnotslots.hotbar.HotbarBinding;
import com.cappleapple.stacksnotslots.category.CategoryMatcher;
import com.cappleapple.stacksnotslots.network.ModNetwork;
import com.cappleapple.stacksnotslots.inventory.DynamicCapacityInventory;
import com.cappleapple.stacksnotslots.inventory.InsertionContext;
import com.cappleapple.stacksnotslots.inventory.InventoryTransactions;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.core.NonNullList;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.StackedContents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Keeps vanilla's 36 item indices as a projection window; the logical collection remains authoritative. */
@Mixin(Inventory.class)
public abstract class InventoryMixin {
    @Shadow @Final public Player player;
    @Shadow @Final public NonNullList<ItemStack> items;

    @Unique
    private PlayerInventoryData sns$data() { return player.getData(ModAttachments.PLAYER_DATA); }

    @Unique
    private boolean sns$active() { return sns$data().migratedVanillaInventory(); }

    @Unique
    private int sns$logicalIndex(int vanillaSlot) {
        DynamicCapacityInventory inventory = sns$data().inventory();
        if (vanillaSlot >= 0 && vanillaSlot < 9 && sns$data().hotbar().get(vanillaSlot).type() != BindingType.EMPTY) {
            int index = sns$data().hotbar().resolveIndex(vanillaSlot, inventory, sns$data().categories());
            if (index >= 0) return index;
        }
        return vanillaSlot;
    }

    @Inject(method = "getItem", at = @At("HEAD"), cancellable = true)
    private void sns$getItem(int slot, CallbackInfoReturnable<ItemStack> callback) {
        if (sns$active() && slot >= 0 && slot < Inventory.INVENTORY_SIZE) {
            callback.setReturnValue(sns$data().inventory().vanillaStackReference(sns$logicalIndex(slot)));
        }
    }

    @Inject(method = "getSelected", at = @At("HEAD"), cancellable = true)
    private void sns$getSelected(CallbackInfoReturnable<ItemStack> callback) {
        if (sns$active()) callback.setReturnValue(sns$data().inventory().vanillaStackReference(sns$logicalIndex(((Inventory)(Object)this).selected)));
    }

    @Inject(method = "setItem", at = @At("HEAD"), cancellable = true)
    private void sns$setItem(int slot, ItemStack stack, CallbackInfo callback) {
        if (!sns$active() || slot < 0 || slot >= Inventory.INVENTORY_SIZE) return;
        DynamicCapacityInventory inventory = sns$data().inventory();
        int index = sns$logicalIndex(slot);
        if (index < inventory.syntheticSlotCount()) {
            inventory.replaceSyntheticSlotFromItemUse(index, stack);
            sns$followHeldItemReplacement(slot, stack);
        }
        else if (!stack.isEmpty()) {
            InsertionResult result = InventoryTransactions.insert(player, stack, InsertionContext.MANUAL_TRANSFER, false);
            if (!result.acceptedAll()) player.drop(result.remainder(), false);
        }
        callback.cancel();
    }

    @Unique
    private void sns$followHeldItemReplacement(int vanillaSlot, ItemStack replacement) {
        if (vanillaSlot < 0 || vanillaSlot >= 9 || replacement.isEmpty()) return;
        HotbarBinding binding = sns$data().hotbar().get(vanillaSlot);
        HotbarBinding updated = switch (binding.type()) {
            case ITEM -> new HotbarBinding(BindingType.ITEM, BuiltInRegistries.ITEM.getKey(replacement.getItem()), null).withSelected(replacement);
            case CATEGORY -> {
                var category = sns$data().categories().find(binding.target());
                yield category != null && CategoryMatcher.matches(category, replacement) ? binding.withSelected(replacement) : binding;
            }
            case FILTER, EMPTY -> binding;
        };
        if (updated == binding) return;
        sns$data().hotbar().set(vanillaSlot, updated);
        if (player instanceof ServerPlayer serverPlayer) ModNetwork.sendMetadata(serverPlayer);
    }

    @Inject(method = "add(Lnet/minecraft/world/item/ItemStack;)Z", at = @At("HEAD"), cancellable = true)
    private void sns$add(ItemStack stack, CallbackInfoReturnable<Boolean> callback) {
        if (!sns$active()) return;
        InsertionResult result = InventoryTransactions.insert(player, stack, InsertionContext.MANUAL_TRANSFER, false);
        stack.setCount(result.remainder().getCount());
        callback.setReturnValue(result.acceptedAnything());
    }

    @Inject(method = "add(ILnet/minecraft/world/item/ItemStack;)Z", at = @At("HEAD"), cancellable = true)
    private void sns$addAt(int slot, ItemStack stack, CallbackInfoReturnable<Boolean> callback) {
        if (!sns$active()) return;
        InsertionResult result = InventoryTransactions.insert(player, stack, InsertionContext.MANUAL_TRANSFER, false);
        stack.setCount(result.remainder().getCount());
        callback.setReturnValue(result.acceptedAnything());
    }

    @Inject(method = "removeItem", at = @At("HEAD"), cancellable = true)
    private void sns$removeItem(int slot, int amount, CallbackInfoReturnable<ItemStack> callback) {
        if (sns$active() && slot >= 0 && slot < Inventory.INVENTORY_SIZE) {
            callback.setReturnValue(sns$data().inventory().extractSyntheticSlot(sns$logicalIndex(slot), amount, false));
        }
    }

    @Inject(method = "removeItemNoUpdate", at = @At("HEAD"), cancellable = true)
    private void sns$removeItemNoUpdate(int slot, CallbackInfoReturnable<ItemStack> callback) {
        if (!sns$active() || slot < 0 || slot >= Inventory.INVENTORY_SIZE) return;
        DynamicCapacityInventory inventory = sns$data().inventory();
        int index = sns$logicalIndex(slot);
        callback.setReturnValue(inventory.extractSyntheticSlot(index, Integer.MAX_VALUE, false));
    }

    @Inject(method = "removeItem(Lnet/minecraft/world/item/ItemStack;)V", at = @At("HEAD"), cancellable = true)
    private void sns$removeReference(ItemStack stack, CallbackInfo callback) {
        if (!sns$active()) return;
        sns$data().inventory().removeReference(stack);
        callback.cancel();
    }

    @Inject(method = "setChanged", at = @At("TAIL"))
    private void sns$reconcileChangedSlot(CallbackInfo callback) {
        if (sns$active()) sns$data().inventory().reconcileExternalMutations();
    }

    @Inject(method = "getFreeSlot", at = @At("HEAD"), cancellable = true)
    private void sns$getFreeSlot(CallbackInfoReturnable<Integer> callback) {
        if (sns$active()) callback.setReturnValue(sns$data().inventory().syntheticSlotCount() < Inventory.INVENTORY_SIZE
                ? sns$data().inventory().syntheticSlotCount() : -1);
    }

    @Inject(method = "findSlotMatchingItem", at = @At("HEAD"), cancellable = true)
    private void sns$findMatching(ItemStack stack, CallbackInfoReturnable<Integer> callback) {
        if (!sns$active()) return;
        int index = sns$data().inventory().indexOf(stack);
        callback.setReturnValue(index >= 0 && index < Inventory.INVENTORY_SIZE ? index : -1);
    }

    @Inject(method = "getSlotWithRemainingSpace", at = @At("HEAD"), cancellable = true)
    private void sns$remainingSpace(ItemStack stack, CallbackInfoReturnable<Integer> callback) {
        if (!sns$active()) return;
        List<ItemStack> stacks = sns$data().inventory().backingStacks();
        for (int i = 0; i < Math.min(stacks.size(), Inventory.INVENTORY_SIZE); i++) {
            ItemStack stored = stacks.get(i);
            if (ItemStack.isSameItemSameComponents(stored, stack) && stored.getCount() < stored.getMaxStackSize()) {
                callback.setReturnValue(i);
                return;
            }
        }
        callback.setReturnValue(-1);
    }

    @Inject(method = "getDestroySpeed", at = @At("HEAD"), cancellable = true)
    private void sns$destroySpeed(BlockState state, CallbackInfoReturnable<Float> callback) {
        if (sns$active()) callback.setReturnValue(((Inventory)(Object)this).getSelected().getDestroySpeed(state));
    }

    @Inject(method = "isEmpty", at = @At("HEAD"), cancellable = true)
    private void sns$isEmpty(CallbackInfoReturnable<Boolean> callback) {
        if (sns$active() && sns$data().inventory().syntheticSlotCount() > 0) callback.setReturnValue(false);
    }

    @Inject(method = "contains(Lnet/minecraft/world/item/ItemStack;)Z", at = @At("HEAD"), cancellable = true)
    private void sns$containsStack(ItemStack stack, CallbackInfoReturnable<Boolean> callback) {
        if (sns$active() && sns$data().inventory().indexOf(stack) >= 0) callback.setReturnValue(true);
    }

    @Inject(method = "contains(Lnet/minecraft/tags/TagKey;)Z", at = @At("HEAD"), cancellable = true)
    private void sns$containsTag(TagKey<Item> tag, CallbackInfoReturnable<Boolean> callback) {
        if (sns$active() && sns$data().inventory().backingStacks().stream().anyMatch(stack -> stack.is(tag))) callback.setReturnValue(true);
    }

    @Inject(method = "contains(Ljava/util/function/Predicate;)Z", at = @At("HEAD"), cancellable = true)
    private void sns$containsPredicate(Predicate<ItemStack> predicate, CallbackInfoReturnable<Boolean> callback) {
        if (sns$active() && sns$data().inventory().backingStacks().stream().anyMatch(predicate)) callback.setReturnValue(true);
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void sns$tick(CallbackInfo callback) {
        if (sns$active()) sns$data().inventory().tick(player);
    }

    @Inject(method = "dropAll", at = @At("HEAD"))
    private void sns$dropAll(CallbackInfo callback) {
        if (sns$active()) for (ItemStack stack : sns$data().inventory().drain()) player.drop(stack, true, false);
    }

    @Inject(method = "clearContent", at = @At("HEAD"))
    private void sns$clear(CallbackInfo callback) {
        if (sns$active()) sns$data().inventory().clear();
    }

    @Inject(method = "fillStackedContents", at = @At("HEAD"), cancellable = true)
    private void sns$fillStacked(StackedContents contents, CallbackInfo callback) {
        if (!sns$active()) return;
        sns$data().inventory().backingStacks().forEach(contents::accountSimpleStack);
        callback.cancel();
    }
}
