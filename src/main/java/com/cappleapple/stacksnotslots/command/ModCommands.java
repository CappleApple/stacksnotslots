package com.cappleapple.stacksnotslots.command;

import com.mojang.brigadier.CommandDispatcher;
import com.cappleapple.stacksnotslots.category.CategoryPresetManager;
import com.cappleapple.stacksnotslots.StacksNotSlots;
import com.cappleapple.stacksnotslots.data.ModAttachments;
import com.cappleapple.stacksnotslots.inventory.DynamicCapacityInventory;
import com.cappleapple.stacksnotslots.network.ModNetwork;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class ModCommands {
    private ModCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("stacksnotslots")
                .then(Commands.literal("capacity")
                        .executes(context -> capacity(context.getSource(), context.getSource().getPlayerOrException()))
                        .then(Commands.argument("player", EntityArgument.player())
                                .requires(source -> source.hasPermission(2))
                                .executes(context -> capacity(context.getSource(), EntityArgument.getPlayer(context, "player")))))
                .then(Commands.literal("inventory")
                        .then(Commands.literal("debug").executes(context -> debug(context.getSource())))
                        .then(Commands.literal("validate").executes(context -> validate(context.getSource()))))
                .then(Commands.literal("categories")
                        .then(Commands.literal("reset").executes(context -> resetCategories(context.getSource())))
                        .then(Commands.literal("reload").requires(source -> source.hasPermission(2)).executes(context -> reloadCategories(context.getSource())))));
    }

    private static int capacity(CommandSourceStack source, ServerPlayer player) {
        DynamicCapacityInventory inventory = player.getData(ModAttachments.PLAYER_DATA).inventory();
        long excess = Math.max(0, inventory.usedCapacity() - inventory.capacity());
        source.sendSuccess(() -> Component.literal(player.getGameProfile().getName() + ": " + inventory.usedCapacity() + " / " + inventory.capacity()
                + (excess > 0 ? " (OVER CAPACITY by " + excess + ")" : "")), false);
        return (int)Math.min(Integer.MAX_VALUE, inventory.remainingCapacity());
    }

    private static int debug(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        DynamicCapacityInventory inventory = player.getData(ModAttachments.PLAYER_DATA).inventory();
        source.sendSuccess(() -> Component.literal("Logical entries: " + inventory.entries().size()
                + ", backing stacks: " + inventory.syntheticSlotCount()
                + ", compatibility slots: " + inventory.compatibilitySlotCount()
                + ", revision: " + inventory.revision()), false);
        return inventory.entries().size();
    }

    private static int validate(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        DynamicCapacityInventory inventory = source.getPlayerOrException().getData(ModAttachments.PLAYER_DATA).inventory();
        java.util.List<String> errors = inventory.validationErrors();
        boolean valid = errors.isEmpty();
        if (!valid) StacksNotSlots.LOGGER.error("Inventory invariant failure for {}: {}", source.getTextName(), errors);
        source.sendSuccess(() -> Component.literal(valid ? "Inventory invariants are valid." : "Inventory invariant failure: " + errors.getFirst()), false);
        return valid ? 1 : 0;
    }

    private static int resetCategories(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        CategoryPresetManager.reset(player.getData(ModAttachments.PLAYER_DATA).categories());
        ModNetwork.sendMetadata(player);
        source.sendSuccess(() -> Component.literal("Categories reset to the current server defaults."), false);
        return 1;
    }

    private static int reloadCategories(CommandSourceStack source) {
        CategoryPresetManager.reload();
        source.sendSuccess(() -> Component.literal("Reloaded default category presets. Existing player customizations were not overwritten."), true);
        return CategoryPresetManager.defaults().size();
    }
}
