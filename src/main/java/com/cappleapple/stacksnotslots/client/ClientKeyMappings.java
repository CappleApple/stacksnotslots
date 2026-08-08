package com.cappleapple.stacksnotslots.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

public final class ClientKeyMappings {
    public static final String CATEGORY = "key.categories.stacksnotslots";
    public static final KeyMapping FOCUS_SEARCH = new KeyMapping("key.stacksnotslots.focus_search", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_F, CATEGORY);
    public static final KeyMapping CYCLE_FORWARD = new KeyMapping("key.stacksnotslots.cycle_forward", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_BRACKET, CATEGORY);
    public static final KeyMapping CYCLE_BACKWARD = new KeyMapping("key.stacksnotslots.cycle_backward", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_LEFT_BRACKET, CATEGORY);

    private ClientKeyMappings() {}

    public static void register(RegisterKeyMappingsEvent event) {
        event.register(FOCUS_SEARCH);
        event.register(CYCLE_FORWARD);
        event.register(CYCLE_BACKWARD);
    }
}
