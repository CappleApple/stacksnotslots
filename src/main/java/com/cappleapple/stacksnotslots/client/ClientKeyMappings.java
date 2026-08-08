package com.cappleapple.stacksnotslots.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyModifier;
import org.lwjgl.glfw.GLFW;

public final class ClientKeyMappings {
    public static final String CATEGORY = "key.categories.stacksnotslots";
    public static final KeyMapping TOGGLE_BROWSER = new KeyMapping("key.stacksnotslots.toggle_browser",
            KeyConflictContext.GUI, KeyModifier.CONTROL, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_F, CATEGORY);
    public static final KeyMapping CYCLE_FORWARD = new KeyMapping("key.stacksnotslots.cycle_forward", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_BRACKET, CATEGORY);
    public static final KeyMapping CYCLE_BACKWARD = new KeyMapping("key.stacksnotslots.cycle_backward", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_LEFT_BRACKET, CATEGORY);
    public static final KeyMapping DUMP_TO_CONTAINER = new KeyMapping("key.stacksnotslots.dump_to_container",
            KeyConflictContext.UNIVERSAL, KeyModifier.CONTROL, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, CATEGORY);
    public static final KeyMapping EXTRACT_FROM_CONTAINER = new KeyMapping("key.stacksnotslots.extract_from_container",
            KeyConflictContext.UNIVERSAL, KeyModifier.CONTROL, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_H, CATEGORY);

    private ClientKeyMappings() {}

    public static void register(RegisterKeyMappingsEvent event) {
        event.register(TOGGLE_BROWSER);
        event.register(CYCLE_FORWARD);
        event.register(CYCLE_BACKWARD);
        event.register(DUMP_TO_CONTAINER);
        event.register(EXTRACT_FROM_CONTAINER);
    }
}
