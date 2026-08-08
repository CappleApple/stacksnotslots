package com.cappleapple.stacksnotslots.client;

import com.cappleapple.stacksnotslots.client.screen.CapacityInventoryScreen;
import com.cappleapple.stacksnotslots.config.ClientConfig;
import com.cappleapple.stacksnotslots.network.HotbarCyclePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.network.PacketDistributor;

public final class ClientEvents {
    private ClientEvents() {}

    @SubscribeEvent
    public static void openingScreen(ScreenEvent.Opening event) {
        if (event.getNewScreen() != null && event.getNewScreen().getClass() == InventoryScreen.class && Minecraft.getInstance().player != null) {
            event.setNewScreen(new CapacityInventoryScreen(Minecraft.getInstance().player));
        }
    }

    @SubscribeEvent
    public static void clientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        while (ClientKeyMappings.FOCUS_SEARCH.consumeClick()) {
            if (minecraft.screen instanceof CapacityInventoryScreen screen) screen.focusSearch();
            else if (minecraft.player != null && minecraft.screen == null) minecraft.setScreen(new CapacityInventoryScreen(minecraft.player));
        }
        if (minecraft.player == null) return;
        while (ClientKeyMappings.CYCLE_FORWARD.consumeClick()) {
            PacketDistributor.sendToServer(new HotbarCyclePayload(minecraft.player.getInventory().selected, 1));
        }
        while (ClientKeyMappings.CYCLE_BACKWARD.consumeClick()) {
            PacketDistributor.sendToServer(new HotbarCyclePayload(minecraft.player.getInventory().selected, -1));
        }
    }

    @SubscribeEvent
    public static void renderHud(RenderGuiEvent.Post event) {
        if (!ClientConfig.HOTBAR_CYCLE_OVERLAY.getAsBoolean()) return;
        ClientTransientState.CycleOverlay overlay = ClientTransientState.cycleOverlay();
        if (overlay == null) return;
        Minecraft minecraft = Minecraft.getInstance();
        GuiGraphics graphics = event.getGuiGraphics();
        int width = Math.max(minecraft.font.width(overlay.bindingName()), minecraft.font.width(overlay.selected().getHoverName())) + 34;
        int x = (graphics.guiWidth() - width) / 2;
        int y = graphics.guiHeight() - 72;
        graphics.fill(x, y, x + width, y + 34, 0xB0101010);
        graphics.renderItem(overlay.selected(), x + 5, y + 9);
        graphics.drawString(minecraft.font, overlay.bindingName(), x + 26, y + 5, 0xAAAAAA, false);
        graphics.drawString(minecraft.font, overlay.selected().isEmpty() ? Component.translatable("gui.stacksnotslots.empty") : overlay.selected().getHoverName(), x + 26, y + 18, 0xFFFFFF, false);
    }

    @SubscribeEvent
    public static void renderContainerOverlay(ScreenEvent.Render.Post event) {
        ContainerInventoryOverlay.render(event);
    }

    @SubscribeEvent
    public static void clickContainerOverlay(ScreenEvent.MouseButtonPressed.Pre event) {
        ContainerInventoryOverlay.click(event);
    }

    @SubscribeEvent
    public static void scrollContainerOverlay(ScreenEvent.MouseScrolled.Pre event) {
        ContainerInventoryOverlay.scroll(event);
    }
}
