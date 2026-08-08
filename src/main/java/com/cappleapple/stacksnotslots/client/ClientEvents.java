package com.cappleapple.stacksnotslots.client;

import com.cappleapple.stacksnotslots.client.screen.CapacityInventoryScreen;
import com.cappleapple.stacksnotslots.config.ClientConfig;
import com.cappleapple.stacksnotslots.network.HotbarCyclePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.EventPriority;
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
        if (minecraft.player == null) return;
        while (ClientKeyMappings.CYCLE_FORWARD.consumeClick()) {
            PacketDistributor.sendToServer(new HotbarCyclePayload(minecraft.player.getInventory().selected, 1));
        }
        while (ClientKeyMappings.CYCLE_BACKWARD.consumeClick()) {
            PacketDistributor.sendToServer(new HotbarCyclePayload(minecraft.player.getInventory().selected, -1));
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void renderHud(RenderGuiEvent.Post event) {
        if (Minecraft.getInstance().screen != null) return;
        renderCycleOverlay(event.getGuiGraphics());
    }

    private static void renderCycleOverlay(GuiGraphics graphics) {
        if (!ClientConfig.HOTBAR_CYCLE_OVERLAY.getAsBoolean()) return;
        ClientTransientState.CycleOverlay overlay = ClientTransientState.cycleOverlay();
        if (overlay == null) return;
        Minecraft minecraft = Minecraft.getInstance();
        int width = Math.max(minecraft.font.width(overlay.bindingName()), minecraft.font.width(overlay.selected().getHoverName())) + 34;
        int x = (graphics.guiWidth() - width) / 2;
        int y = graphics.guiHeight() - 72;
        graphics.fill(x, y, x + width, y + 34, 0xB0101010);
        graphics.renderItem(overlay.selected(), x + 5, y + 9);
        graphics.drawString(minecraft.font, overlay.bindingName(), x + 26, y + 5, 0xAAAAAA, false);
        graphics.drawString(minecraft.font, overlay.selected().isEmpty() ? Component.translatable("gui.stacksnotslots.empty") : overlay.selected().getHoverName(), x + 26, y + 18, 0xFFFFFF, false);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void renderContainerOverlay(ScreenEvent.Render.Post event) {
        ContainerInventoryOverlay.render(event);
        renderCycleOverlay(event.getGuiGraphics());
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void clickContainerOverlay(ScreenEvent.MouseButtonPressed.Pre event) {
        ContainerInventoryOverlay.click(event);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void scrollContainerOverlay(ScreenEvent.MouseScrolled.Pre event) {
        ContainerInventoryOverlay.scroll(event);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void dragContainerOverlay(ScreenEvent.MouseDragged.Pre event) {
        ContainerInventoryOverlay.drag(event);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void releaseContainerOverlay(ScreenEvent.MouseButtonReleased.Pre event) {
        ContainerInventoryOverlay.release(event);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void keyContainerOverlay(ScreenEvent.KeyPressed.Pre event) {
        ContainerInventoryOverlay.keyPressed(event);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void characterContainerOverlay(ScreenEvent.CharacterTyped.Pre event) {
        ContainerInventoryOverlay.characterTyped(event);
    }
}
