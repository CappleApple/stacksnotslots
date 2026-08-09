package com.cappleapple.stacksnotslots.client;

import com.cappleapple.stacksnotslots.client.screen.CapacityInventoryScreen;
import com.cappleapple.stacksnotslots.config.ClientConfig;
import com.cappleapple.stacksnotslots.network.HotbarCyclePayload;
import com.cappleapple.stacksnotslots.network.BulkTransferPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
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
        KeyBindingCompatibility.warnAboutUnsafeExternalBindings(minecraft);
        while (ClientKeyMappings.CYCLE_FORWARD.consumeClick()) {
            PacketDistributor.sendToServer(new HotbarCyclePayload(minecraft.player.getInventory().selected, 1));
        }
        while (ClientKeyMappings.CYCLE_BACKWARD.consumeClick()) {
            PacketDistributor.sendToServer(new HotbarCyclePayload(minecraft.player.getInventory().selected, -1));
        }
        BulkTransferPayload.Target target = minecraft.screen instanceof AbstractContainerScreen<?>
                ? BulkTransferPayload.Target.OPEN_MENU : BulkTransferPayload.Target.LOOKED_AT;
        while (ClientKeyMappings.DUMP_TO_CONTAINER.consumeClick()) {
            PacketDistributor.sendToServer(new BulkTransferPayload(BulkTransferPayload.Direction.TO_CONTAINER, target));
        }
        while (ClientKeyMappings.EXTRACT_FROM_CONTAINER.consumeClick()) {
            PacketDistributor.sendToServer(new BulkTransferPayload(BulkTransferPayload.Direction.FROM_CONTAINER, target));
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void renderHud(RenderGuiEvent.Post event) {
        if (Minecraft.getInstance().screen != null) return;
        renderCycleOverlay(event.getGuiGraphics());
        renderTransferOverlay(event.getGuiGraphics());
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

    private static void renderTransferOverlay(GuiGraphics graphics) {
        if (!ClientConfig.BULK_TRANSFER_OVERLAY.getAsBoolean()) return;
        ClientTransientState.TransferOverlay overlay = ClientTransientState.transferOverlay();
        if (overlay == null || overlay.stacks().isEmpty()) return;
        Minecraft minecraft = Minecraft.getInstance();
        int shown = Math.min(24, overlay.stacks().size());
        int columns = Math.min(6, shown);
        int rows = Math.ceilDiv(shown, columns);
        int boxWidth = columns * 22 + 8;
        int boxHeight = rows * 22 + 22;
        int x = (graphics.guiWidth() - boxWidth) / 2;
        int y = Math.max(8, graphics.guiHeight() / 2 - boxHeight / 2);
        graphics.fill(x, y, x + boxWidth, y + boxHeight, 0xD0101010);
        Component title = Component.translatable(overlay.direction() == BulkTransferPayload.Direction.TO_CONTAINER
                ? "gui.stacksnotslots.transferred_to_container" : "gui.stacksnotslots.transferred_from_container");
        graphics.drawCenteredString(minecraft.font, title, x + boxWidth / 2, y + 6, 0xFFFFFF);
        for (int index = 0; index < shown; index++) {
            var moved = overlay.stacks().get(index);
            ItemStack stack = moved.prototype();
            int cellX = x + 5 + index % columns * 22;
            int cellY = y + 18 + index / columns * 22;
            graphics.renderItem(stack, cellX, cellY);
            long stacks = Math.ceilDiv(moved.quantity(), Math.max(1, stack.getMaxStackSize()));
            String count = stacks + "S";
            graphics.pose().pushPose();
            graphics.pose().translate(0, 0, 300);
            graphics.pose().scale(0.5F, 0.5F, 1.0F);
            graphics.drawString(minecraft.font, count, (cellX + 18) * 2 - minecraft.font.width(count), (cellY + 11) * 2,
                    0xFFFFFF, true);
            graphics.pose().popPose();
        }
        if (overlay.stacks().size() > shown) {
            graphics.drawString(minecraft.font, "+" + (overlay.stacks().size() - shown), x + boxWidth - 24, y + 6, 0xAAAAAA, false);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void renderContainerOverlay(ScreenEvent.Render.Post event) {
        ContainerInventoryOverlay.render(event);
        renderCycleOverlay(event.getGuiGraphics());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void initializeContainerOverlay(ScreenEvent.Init.Post event) {
        ContainerInventoryOverlay.initialize(event);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void keyContainerOverlay(ScreenEvent.KeyPressed.Pre event) {
        ContainerInventoryOverlay.keyPressed(event);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void releaseKeyContainerOverlay(ScreenEvent.KeyReleased.Pre event) {
        ContainerInventoryOverlay.keyReleased(event);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void characterContainerOverlay(ScreenEvent.CharacterTyped.Pre event) {
        ContainerInventoryOverlay.characterTyped(event);
    }

}
