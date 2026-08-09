package com.cappleapple.stacksnotslots.client;

import com.cappleapple.stacksnotslots.StacksNotSlots;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.Set;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.settings.KeyModifier;
import org.lwjgl.glfw.GLFW;

/** Warns about external bindings that make normal container interaction impossible. */
public final class KeyBindingCompatibility {
    private static final Set<String> SOPHISTICATED_TRANSFER_KEYS = Set.of(
            "key.sophisticatedcore.transfer_to_storage",
            "key.sophisticatedcore.transfer_to_inventory");
    private static boolean checked;

    private KeyBindingCompatibility() {}

    public static void warnAboutUnsafeExternalBindings(Minecraft minecraft) {
        if (checked || minecraft.player == null) return;
        checked = true;
        for (KeyMapping mapping : minecraft.options.keyMappings) {
            if (!SOPHISTICATED_TRANSFER_KEYS.contains(mapping.getName()) || !isPlainLeftClick(mapping)) continue;
            StacksNotSlots.LOGGER.warn(
                    "{} is bound to unmodified left click; Sophisticated container screens will cancel normal clicks until it is rebound",
                    mapping.getName());
            minecraft.player.displayClientMessage(
                    Component.translatable("message.stacksnotslots.unsafe_sophisticated_binding", Component.translatable(mapping.getName())),
                    false);
        }
    }

    private static boolean isPlainLeftClick(KeyMapping mapping) {
        InputConstants.Key key = mapping.getKey();
        return key.getType() == InputConstants.Type.MOUSE
                && key.getValue() == GLFW.GLFW_MOUSE_BUTTON_LEFT
                && mapping.getKeyModifier() == KeyModifier.NONE;
    }
}
