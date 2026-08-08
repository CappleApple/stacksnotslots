package com.cappleapple.stacksnotslots.client;

import com.cappleapple.stacksnotslots.api.LogicalInventoryEntry;
import com.cappleapple.stacksnotslots.client.screen.CapacityInventoryScreen;
import com.cappleapple.stacksnotslots.data.ModAttachments;
import com.cappleapple.stacksnotslots.network.InventoryActionPayload;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Logical inventory access beside ordinary container screens, allowing any entry to be moved through the cursor. */
public final class ContainerInventoryOverlay {
    private static final int WIDTH = 158;
    private static final int ROWS = 7;
    private static final int ROW_HEIGHT = 18;
    private static int scroll;
    private static UUID cachedPlayer;
    private static long cachedRevision = -1;
    private static List<LogicalInventoryEntry> cachedEntries = List.of();

    private ContainerInventoryOverlay() {}

    public static void render(ScreenEvent.Render.Post event) {
        if (!supports(event.getScreen()) || Minecraft.getInstance().player == null) return;
        List<LogicalInventoryEntry> entries = entries();
        scroll = Math.min(scroll, Math.max(0, entries.size() - ROWS));
        Screen screen = event.getScreen();
        GuiGraphics graphics = event.getGuiGraphics();
        int left = screen.width - WIDTH - 4;
        int top = 8;
        graphics.fill(left, top, left + WIDTH, top + 18 + ROWS * ROW_HEIGHT + 16, 0xD0202020);
        graphics.drawString(Minecraft.getInstance().font, Component.translatable("gui.stacksnotslots.inventory_overlay"), left + 5, top + 5, 0xFFFFFF, false);
        LogicalInventoryEntry hovered = null;
        for (int row = 0; row < ROWS && row + scroll < entries.size(); row++) {
            LogicalInventoryEntry entry = entries.get(row + scroll);
            int y = top + 17 + row * ROW_HEIGHT;
            if (event.getMouseX() >= left + 2 && event.getMouseX() < left + WIDTH - 2 && event.getMouseY() >= y && event.getMouseY() < y + ROW_HEIGHT) {
                graphics.fill(left + 2, y, left + WIDTH - 2, y + ROW_HEIGHT, 0x804F72A5);
                hovered = entry;
            }
            graphics.renderItem(entry.representative(), left + 3, y + 1);
            graphics.drawString(Minecraft.getInstance().font,
                    Minecraft.getInstance().font.plainSubstrByWidth(entry.representative().getHoverName().getString(), 98),
                    left + 23, y + 5, 0xFFFFFF, false);
            String count = Long.toString(entry.quantity());
            graphics.drawString(Minecraft.getInstance().font, count, left + WIDTH - 5 - Minecraft.getInstance().font.width(count), y + 5, 0xDDDDDD, false);
        }
        var inventory = Minecraft.getInstance().player.getData(ModAttachments.PLAYER_DATA).inventory();
        graphics.drawString(Minecraft.getInstance().font, inventory.usedCapacity() + " / " + inventory.capacity(), left + 5, top + 20 + ROWS * ROW_HEIGHT, 0xB8B8B8, false);
        if (hovered != null) graphics.renderTooltip(Minecraft.getInstance().font, hovered.representative(), event.getMouseX(), event.getMouseY());
    }

    public static void click(ScreenEvent.MouseButtonPressed.Pre event) {
        if (!supports(event.getScreen()) || Minecraft.getInstance().player == null || (event.getButton() != 0 && event.getButton() != 1)) return;
        LogicalInventoryEntry entry = entryAt(event.getScreen(), event.getMouseX(), event.getMouseY());
        if (entry == null) return;
        PacketDistributor.sendToServer(new InventoryActionPayload(
                event.getButton() == 1 ? InventoryActionPayload.Action.TAKE_HALF : InventoryActionPayload.Action.TAKE_STACK,
                entry.representative()));
        event.setCanceled(true);
    }

    public static void scroll(ScreenEvent.MouseScrolled.Pre event) {
        if (!supports(event.getScreen())) return;
        int left = event.getScreen().width - WIDTH - 4;
        if (event.getMouseX() < left || event.getMouseY() < 8 || event.getMouseY() > 8 + 18 + ROWS * ROW_HEIGHT) return;
        int maximum = Math.max(0, entries().size() - ROWS);
        scroll = Math.max(0, Math.min(maximum, scroll - (int)Math.signum(event.getScrollDeltaY())));
        event.setCanceled(true);
    }

    private static LogicalInventoryEntry entryAt(Screen screen, double mouseX, double mouseY) {
        int left = screen.width - WIDTH - 4;
        int top = 8;
        if (mouseX < left + 2 || mouseX >= left + WIDTH - 2 || mouseY < top + 17 || mouseY >= top + 17 + ROWS * ROW_HEIGHT) return null;
        int index = scroll + ((int)mouseY - top - 17) / ROW_HEIGHT;
        List<LogicalInventoryEntry> entries = entries();
        return index >= 0 && index < entries.size() ? entries.get(index) : null;
    }

    private static List<LogicalInventoryEntry> entries() {
        var player = Minecraft.getInstance().player;
        if (player == null) return List.of();
        var inventory = player.getData(ModAttachments.PLAYER_DATA).inventory();
        if (player.getUUID().equals(cachedPlayer) && inventory.revision() == cachedRevision) return cachedEntries;
        cachedPlayer = player.getUUID();
        cachedRevision = inventory.revision();
        cachedEntries = inventory.entries().stream()
                .sorted(Comparator.comparing(entry -> entry.representative().getHoverName().getString(), String.CASE_INSENSITIVE_ORDER)).toList();
        return cachedEntries;
    }

    private static boolean supports(Screen screen) {
        return screen instanceof AbstractContainerScreen<?> && !(screen instanceof InventoryScreen) && !(screen instanceof CapacityInventoryScreen);
    }
}
