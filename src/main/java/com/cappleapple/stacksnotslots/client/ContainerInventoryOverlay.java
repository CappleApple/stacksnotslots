package com.cappleapple.stacksnotslots.client;

import com.cappleapple.stacksnotslots.StacksNotSlots;
import com.cappleapple.stacksnotslots.api.LogicalInventoryEntry;
import com.cappleapple.stacksnotslots.category.CategoryDefinition;
import com.cappleapple.stacksnotslots.category.CategoryMatcher;
import com.cappleapple.stacksnotslots.category.SortMode;
import com.cappleapple.stacksnotslots.client.screen.CategoryIcons;
import com.cappleapple.stacksnotslots.client.screen.CategoryManagerScreen;
import com.cappleapple.stacksnotslots.client.screen.InventoryBrowserSettingsScreen;
import com.cappleapple.stacksnotslots.config.ClientConfig;
import com.cappleapple.stacksnotslots.data.ModAttachments;
import com.cappleapple.stacksnotslots.network.BrowserStatePayload;
import com.cappleapple.stacksnotslots.network.BrowserTransferPayload;
import com.cappleapple.stacksnotslots.network.BulkTransferPayload;
import com.cappleapple.stacksnotslots.network.InventoryActionPayload;
import com.cappleapple.stacksnotslots.network.InventoryViewPreferencesPayload;
import com.cappleapple.stacksnotslots.network.StowSlotPayload;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/** A topmost logical-inventory browser whose native listener owns only its visible bounds. */
public final class ContainerInventoryOverlay {
    private static final int MAX_QUERY = 96;
    private static final double DRAG_THRESHOLD = 5.0;
    private static final String BUILT_IN_LOGO_ICON = "stacksnotslots:logo";
    private static final ResourceLocation LOGO_TEXTURE = StacksNotSlots.id("textures/gui/stacks-not-slots-logo.png");
    private static final BrowserSearchQuery SEARCH = new BrowserSearchQuery(MAX_QUERY);
    private static Screen lastScreen;
    private static Screen stateSyncedScreen;
    private static String activeScreenType;
    private static int anchoredGuiLeft;
    private static int anchoredGuiTop;
    private static int handleX;
    private static int handleY;
    private static boolean open;
    private static boolean visible;
    private static ClientConfig.BrowserDockSide dockSide = ClientConfig.BrowserDockSide.RIGHT;
    private static boolean searchFocused;
    private static int suppressedTypedKey = GLFW.GLFW_KEY_UNKNOWN;
    private static boolean handlePressed;
    private static boolean dragging;
    private static int consumedReleaseButton = -1;
    private static Screen capturedPressScreen;
    private static double dragOffsetX;
    private static double dragOffsetY;
    private static double pressX;
    private static double pressY;
    private static int scroll;
    private static UUID cachedPlayer;
    private static long cachedRevision = -1;
    private static String cachedQuery = "";
    private static ResourceLocation cachedCategory;
    private static SortMode cachedSort;
    private static List<LogicalInventoryEntry> cachedEntries = List.of();
    private static LogicalInventoryEntry hoveredEntry;
    private static CategoryDefinition hoveredCategory;
    private static String hoveredControl;

    private ContainerInventoryOverlay() {}

    /** Keeps server browser state in sync without depending on a screen's child-input implementation. */
    public static void initialize(ScreenEvent.Init.Post event) {
        if (!supports(event.getScreen())) return;
        migrateLegacyHandleIconDefault();
        ensurePosition(event.getScreen());
        if (stateSyncedScreen != event.getScreen()) {
            stateSyncedScreen = event.getScreen();
            PacketDistributor.sendToServer(new BrowserStatePayload(isOpen()));
        }
    }

    /**
     * Routes input before any concrete container screen sees it. Modded screens are not required to
     * dispatch through {@link net.minecraft.client.gui.components.events.ContainerEventHandler}, so
     * registering this overlay as a child widget is not universal. Only exact visible overlay bounds
     * are consumed here; hiding the browser removes all of its mouse participation.
     */
    public static boolean mouseButton(int button, int action) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!supports(minecraft.screen) || minecraft.player == null) return false;
        Screen screen = minecraft.screen;
        double mouseX = scaledMouseX(minecraft);
        double mouseY = scaledMouseY(minecraft);
        if (action == GLFW.GLFW_RELEASE) return releasePointer(screen, mouseX, mouseY, button);
        if (action != GLFW.GLFW_PRESS) return false;

        // A screen transition or another mod can suppress the matching release callback. Never let
        // stale overlay capture leak into a subsequent native click.
        if (consumedReleaseButton != -1) clearPointerCapture();
        return mouseClicked((AbstractContainerScreen<?>)screen, mouseX, mouseY, button);
    }

    /** Handles browser scrolling before ingredient overlays can claim the same topmost pixels. */
    public static boolean mouseScrolled(double deltaY) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!supports(minecraft.screen)) return false;
        return mouseScrolled((AbstractContainerScreen<?>)minecraft.screen,
                scaledMouseX(minecraft), scaledMouseY(minecraft), deltaY);
    }

    public static void render(ScreenEvent.Render.Post event) {
        if (!supports(event.getScreen()) || Minecraft.getInstance().player == null) return;
        ensurePosition(event.getScreen());
        if (!visible) return;
        GuiGraphics graphics = event.getGuiGraphics();
        hoveredEntry = null;
        hoveredCategory = null;
        hoveredControl = null;
        updateDrag(event.getScreen(), event.getMouseX(), event.getMouseY());
        // Some custom container screens leave depth-tested GUI geometry at the default layer. Keep
        // rendering and hit testing consistent by drawing every owned browser pixel above it.
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 300);
        try {
            if (open) renderPanel((AbstractContainerScreen<?>)event.getScreen(), graphics, event.getMouseX(), event.getMouseY());
            renderHandle(graphics, event.getMouseX(), event.getMouseY());
            if (hoveredEntry != null) graphics.renderTooltip(Minecraft.getInstance().font, hoveredEntry.representative(), event.getMouseX(), event.getMouseY());
            else if (hoveredCategory != null) graphics.renderTooltip(Minecraft.getInstance().font, Component.literal(hoveredCategory.displayName()), event.getMouseX(), event.getMouseY());
            else if (hoveredControl != null) graphics.renderTooltip(Minecraft.getInstance().font, Component.translatable(hoveredControl), event.getMouseX(), event.getMouseY());
        } finally {
            graphics.pose().popPose();
        }
    }

    private static boolean releasePointer(Screen screen, double mouseX, double mouseY, int button) {
        if (button != consumedReleaseButton) return false;
        Screen pressScreen = capturedPressScreen;
        if (handlePressed && pressScreen == screen && button == 0) updateDrag(screen, mouseX, mouseY);
        boolean wasHandlePress = handlePressed;
        boolean wasDragging = dragging;
        clearPointerCapture();
        if (pressScreen != screen) return false;
        if (wasHandlePress && button == 0) {
            if (wasDragging) persistScreenState(screen);
            else setOpen(!open, screen);
        }
        return true;
    }

    public static void keyPressed(ScreenEvent.KeyPressed.Pre event) {
        if (!supports(event.getScreen())) return;
        InputConstants.Key pressedKey = InputConstants.getKey(event.getKeyCode(), event.getScanCode());
        if (ClientKeyMappings.TOGGLE_BROWSER.isActiveAndMatches(pressedKey)) {
            if (open) {
                setOpen(false, event.getScreen());
                visible = false;
            } else {
                visible = !visible;
            }
            persistScreenState(event.getScreen());
            clearPointerCapture();
            searchFocused = false;
            SEARCH.clearSelection();
            event.setCanceled(true);
            return;
        }
        if (!searchFocused && !(event.getScreen().getFocused() instanceof EditBox)
                && ClientKeyMappings.SEARCH_BROWSER.isActiveAndMatches(pressedKey)) {
            suppressedTypedKey = event.getKeyCode();
            beginNewSearch(event.getScreen());
            event.setCanceled(true);
            return;
        }
        if (open && !searchFocused && hoveredEntry != null
                && Minecraft.getInstance().options.keyDrop.matches(event.getKeyCode(), event.getScanCode())) {
            PacketDistributor.sendToServer(new InventoryActionPayload(Screen.hasControlDown()
                    ? InventoryActionPayload.Action.DROP_STACK : InventoryActionPayload.Action.DROP_ONE,
                    hoveredEntry.representative()));
            event.setCanceled(true);
            return;
        }
        if (!searchFocused || !open) return;
        if (Minecraft.getInstance().options.keyInventory.matches(event.getKeyCode(), event.getScanCode())) {
            event.setCanceled(true);
            return;
        }
        if (event.getKeyCode() == GLFW.GLFW_KEY_A && Screen.hasControlDown()) {
            SEARCH.selectAll();
            event.setCanceled(true);
            return;
        }
        switch (event.getKeyCode()) {
            case GLFW.GLFW_KEY_ESCAPE -> {
                searchFocused = false;
                SEARCH.clearSelection();
            }
            case GLFW.GLFW_KEY_BACKSPACE -> updateSearch(SEARCH.backspace());
            case GLFW.GLFW_KEY_DELETE -> updateSearch(SEARCH.deleteSelection());
            default -> { return; }
        }
        event.setCanceled(true);
    }

    public static void keyReleased(ScreenEvent.KeyReleased.Pre event) {
        if (event.getKeyCode() == suppressedTypedKey) suppressedTypedKey = GLFW.GLFW_KEY_UNKNOWN;
    }

    public static void characterTyped(ScreenEvent.CharacterTyped.Pre event) {
        if (!supports(event.getScreen()) || !open || !searchFocused) return;
        if (suppressedTypedKey != GLFW.GLFW_KEY_UNKNOWN) {
            suppressedTypedKey = GLFW.GLFW_KEY_UNKNOWN;
            event.setCanceled(true);
            return;
        }
        if (SEARCH.append(event.getCodePoint())) {
            updateSearch(true);
            event.setCanceled(true);
        }
    }

    private static boolean isOpen() { return open && visible; }

    /** Exact topmost input region, used by the custom inventory selector to avoid competing when overlapped. */
    public static boolean ownsPoint(Screen screen, double mouseX, double mouseY) {
        if (!supports(screen)) return false;
        ensurePosition(screen);
        if (!visible) return false;
        if (inside(mouseX, mouseY, handleX, handleY,
                BrowserPanelLayout.HANDLE_WIDTH, BrowserPanelLayout.HANDLE_HEIGHT)) return true;
        if (!open) return false;
        Rect2i panel = panelBounds((AbstractContainerScreen<?>)screen);
        return inside(mouseX, mouseY, panel.getX(), panel.getY(), panel.getWidth(), panel.getHeight());
    }

    /** Exact dynamic areas so ingredient overlays wrap each floating element instead of reserving the gap between them. */
    public static List<Rect2i> currentAreas(Screen screen) {
        if (!supports(screen)) return List.of();
        ensurePosition(screen);
        if (!visible) return List.of();
        Rect2i handle = new Rect2i(handleX, handleY,
                BrowserPanelLayout.HANDLE_WIDTH, BrowserPanelLayout.HANDLE_HEIGHT);
        if (!open) return List.of(handle);
        return List.of(handle, panelBounds((AbstractContainerScreen<?>)screen));
    }

    private static boolean mouseClicked(AbstractContainerScreen<?> screen, double mouseX, double mouseY, int button) {
        ensurePosition(screen);
        if (visible && inside(mouseX, mouseY, handleX, handleY,
                BrowserPanelLayout.HANDLE_WIDTH, BrowserPanelLayout.HANDLE_HEIGHT)) {
            capturePointer(screen, button);
            if (button == 0) {
                handlePressed = true;
                dragOffsetX = mouseX - handleX;
                dragOffsetY = mouseY - handleY;
                pressX = mouseX;
                pressY = mouseY;
            }
            return true;
        }
        if (!visible || !open) return false;
        BrowserPanelLayout layout = layout(screen);
        if (!inside(mouseX, mouseY, layout.panelX(), layout.panelY(), layout.panelWidth(), layout.panelHeight())) {
            searchFocused = false;
            SEARCH.clearSelection();
            return false;
        }

        capturePointer(screen, button);
        if (inside(mouseX, mouseY, layout.searchX(), layout.searchY(), layout.searchWidth(), BrowserPanelLayout.CONTROL_HEIGHT)) {
            searchFocused = true;
            if (button == 1) updateSearch(SEARCH.clear());
            else SEARCH.clearSelection();
            return true;
        }
        searchFocused = false;
        SEARCH.clearSelection();
        if (layout.category().contains(mouseX, mouseY)) {
            changeCategory(button == 1 ? -1 : 1);
            return true;
        }
        if (layout.sort().contains(mouseX, mouseY)) {
            cycleSort();
            return true;
        }
        if (layout.transfer().contains(mouseX, mouseY)) {
            PacketDistributor.sendToServer(new BulkTransferPayload(Screen.hasShiftDown()
                    ? BulkTransferPayload.Direction.TO_CONTAINER : BulkTransferPayload.Direction.FROM_CONTAINER,
                    BulkTransferPayload.Target.OPEN_MENU));
            return true;
        }
        if (layout.direction().contains(mouseX, mouseY)) {
            cycleDockSide(screen);
            return true;
        }
        if (layout.manage().contains(mouseX, mouseY)) {
            Minecraft.getInstance().setScreen(new CategoryManagerScreen(screen, Minecraft.getInstance().player));
            return true;
        }
        if (layout.settings().contains(mouseX, mouseY)) {
            Minecraft.getInstance().setScreen(new InventoryBrowserSettingsScreen(screen));
            return true;
        }

        if (inside(mouseX, mouseY, layout.contentX(), layout.contentY(), layout.contentWidth(), layout.contentHeight())
                && (button == 0 || button == 1)
                && !Minecraft.getInstance().player.containerMenu.getCarried().isEmpty()) {
            PacketDistributor.sendToServer(new StowSlotPayload(-1));
            return true;
        }
        LogicalInventoryEntry entry = entryAt(screen, mouseX, mouseY);
        if (entry != null && (button == 0 || button == 1)) {
            ItemStack carried = Minecraft.getInstance().player.containerMenu.getCarried();
            if (!carried.isEmpty()) PacketDistributor.sendToServer(new StowSlotPayload(-1));
            else if (Screen.hasShiftDown()) PacketDistributor.sendToServer(new BrowserTransferPayload(entry.representative()));
            else PacketDistributor.sendToServer(new InventoryActionPayload(button == 1
                    ? InventoryActionPayload.Action.TAKE_HALF : InventoryActionPayload.Action.TAKE_STACK, entry.representative()));
        }
        return true;
    }

    private static boolean mouseScrolled(AbstractContainerScreen<?> screen, double mouseX, double mouseY, double deltaY) {
        if (!open || !visible) return false;
        BrowserPanelLayout layout = layout(screen);
        if (layout.category().contains(mouseX, mouseY)) {
            changeCategory(deltaY > 0 ? -1 : 1);
            return true;
        }
        if (!inside(mouseX, mouseY, layout.contentX(), layout.contentY(), layout.contentWidth(), layout.contentHeight())) return false;
        int page = ClientConfig.BROWSER_VIEW_MODE.get() == ClientConfig.BrowserViewMode.GRID
                ? layout.gridColumns() : 1;
        int maximum = Math.max(0, entries().size() - layout.visibleEntryCount());
        scroll = clamp(scroll - (int)Math.signum(deltaY) * page, 0, maximum);
        return true;
    }

    private static void renderPanel(AbstractContainerScreen<?> screen, GuiGraphics graphics, int mouseX, int mouseY) {
        BrowserPanelLayout layout = layout(screen);
        Minecraft minecraft = Minecraft.getInstance();
        var inventory = minecraft.player.getData(ModAttachments.PLAYER_DATA).inventory();
        List<LogicalInventoryEntry> entries = entries();
        scroll = Math.min(scroll, Math.max(0, entries.size() - layout.visibleEntryCount()));

        graphics.fill(layout.panelX(), layout.panelY(), layout.panelX() + layout.panelWidth(), layout.panelY() + layout.panelHeight(), 0xF0C6C6C6);
        graphics.fill(layout.panelX() + 2, layout.panelY() + 2, layout.panelX() + layout.panelWidth() - 2, layout.panelY() + layout.panelHeight() - 2, 0xF0202020);
        graphics.fill(layout.searchX(), layout.searchY(), layout.searchX() + layout.searchWidth(), layout.searchY() + BrowserPanelLayout.CONTROL_HEIGHT,
                searchFocused ? 0xFF101010 : 0xFF282828);
        String query = SEARCH.value();
        String shownQuery = query.isEmpty() && !searchFocused
                ? Component.translatable("gui.stacksnotslots.search_hint_compact").getString() : query;
        String visibleQuery = minecraft.font.plainSubstrByWidth(shownQuery, layout.searchWidth() - 7);
        boolean validQuery = ItemSearchExpression.parse(query).valid();
        if (searchFocused && SEARCH.allSelected()) {
            int selectionWidth = minecraft.font.width(visibleQuery);
            graphics.fill(layout.searchX() + 3, layout.searchY() + 3,
                    layout.searchX() + 4 + selectionWidth, layout.searchY() + 15, 0xFF2F5F8F);
        }
        graphics.drawString(minecraft.font, visibleQuery,
                layout.searchX() + 4, layout.searchY() + 5,
                query.isEmpty() ? 0x888888 : validQuery ? 0xFFFFFF : 0xFF5555, false);
        if (searchFocused && (System.currentTimeMillis() / 500L) % 2 == 0) {
            int cursorX = layout.searchX() + 4 + minecraft.font.width(minecraft.font.plainSubstrByWidth(query, layout.searchWidth() - 7));
            graphics.fill(cursorX, layout.searchY() + 3, cursorX + 1, layout.searchY() + 15, 0xFFFFFFFF);
        }
        if (inside(mouseX, mouseY, layout.searchX(), layout.searchY(),
                layout.searchWidth(), BrowserPanelLayout.CONTROL_HEIGHT)) {
            hoveredControl = "tooltip.stacksnotslots.browser_search";
        }

        renderSquare(graphics, layout.category(), CategoryIcons.displayStack(currentCategory()), mouseX, mouseY,
                "gui.stacksnotslots.category_control");
        renderTextSquare(graphics, layout.sort(), shortSortName(currentSort()), mouseX, mouseY,
                "gui.stacksnotslots.sort_control");
        renderSquare(graphics, layout.transfer(),
                new ItemStack(Screen.hasShiftDown() ? Items.PISTON : Items.STICKY_PISTON), mouseX, mouseY,
                Screen.hasShiftDown() ? "gui.stacksnotslots.dump_to_container" : "gui.stacksnotslots.extract_from_container");
        if (layout.category().contains(mouseX, mouseY)) hoveredCategory = currentCategory();

        if (ClientConfig.BROWSER_VIEW_MODE.get() == ClientConfig.BrowserViewMode.GRID) renderGrid(graphics, layout, entries, mouseX, mouseY);
        else renderList(graphics, layout, entries, mouseX, mouseY);

        long used = inventory.usedCapacity();
        long capacity = inventory.capacity();
        graphics.drawString(minecraft.font, InventoryCountFormatter.overall(used, capacity), layout.panelX() + 5,
                layout.capacityTextY(), used > capacity ? 0xFF7777 : 0xFFFFFF, false);
        int barWidth = layout.panelWidth() - 10;
        int filled = capacity <= 0 ? (used > 0 ? barWidth : 0) : used >= capacity ? barWidth : (int)(used * barWidth / capacity);
        graphics.fill(layout.panelX() + 5, layout.capacityBarY(), layout.panelX() + 5 + barWidth, layout.capacityBarY() + 5, 0xFF454545);
        graphics.fill(layout.panelX() + 5, layout.capacityBarY(), layout.panelX() + 5 + filled, layout.capacityBarY() + 5,
                used > capacity ? 0xFFE34B4B : 0xFF54B45A);
        renderTextSquare(graphics, layout.direction(), arrow(dockSide), mouseX, mouseY,
                "gui.stacksnotslots.browser_direction");
        renderSquare(graphics, layout.manage(), configuredIcon(ClientConfig.MANAGE_TABS_ICON.get()), mouseX, mouseY,
                "gui.stacksnotslots.manage_tabs");
        renderSquare(graphics, layout.settings(), configuredIcon(ClientConfig.SETTINGS_ICON.get()), mouseX, mouseY,
                "gui.stacksnotslots.settings");
    }

    private static void renderGrid(GuiGraphics graphics, BrowserPanelLayout layout, List<LogicalInventoryEntry> entries, int mouseX, int mouseY) {
        Minecraft minecraft = Minecraft.getInstance();
        int columns = layout.gridColumns();
        long capacity = minecraft.player.getData(ModAttachments.PLAYER_DATA).inventory().capacity();
        for (int offset = 0; offset < layout.visibleEntryCount(); offset++) {
            int index = scroll + offset;
            if (index >= entries.size()) break;
            LogicalInventoryEntry entry = entries.get(index);
            int x = layout.contentX() + offset % columns * BrowserPanelLayout.GRID_CELL;
            int y = layout.contentY() + offset / columns * BrowserPanelLayout.GRID_CELL;
            if (inside(mouseX, mouseY, x, y, BrowserPanelLayout.GRID_CELL, BrowserPanelLayout.GRID_CELL)) {
                graphics.fill(x, y, x + BrowserPanelLayout.GRID_CELL, y + BrowserPanelLayout.GRID_CELL, 0x804F72A5);
                hoveredEntry = entry;
            }
            graphics.renderItem(entry.representative(), x + 2, y + 2);
            String count = InventoryCountFormatter.item(entry, capacity);
            graphics.pose().pushPose();
            graphics.pose().translate(0, 0, 250);
            graphics.pose().scale(0.5F, 0.5F, 1.0F);
            graphics.drawString(minecraft.font, count,
                    (x + BrowserPanelLayout.GRID_CELL - 1) * 2 - minecraft.font.width(count), (y + 12) * 2, 0xFFFFFF, true);
            graphics.pose().popPose();
        }
    }

    private static void renderList(GuiGraphics graphics, BrowserPanelLayout layout, List<LogicalInventoryEntry> entries, int mouseX, int mouseY) {
        Minecraft minecraft = Minecraft.getInstance();
        long capacity = minecraft.player.getData(ModAttachments.PLAYER_DATA).inventory().capacity();
        for (int row = 0; row < layout.visibleEntryCount(); row++) {
            int index = scroll + row;
            if (index >= entries.size()) break;
            LogicalInventoryEntry entry = entries.get(index);
            int y = layout.contentY() + row * BrowserPanelLayout.LIST_ROW;
            if (inside(mouseX, mouseY, layout.contentX(), y, layout.contentWidth(), BrowserPanelLayout.LIST_ROW)) {
                graphics.fill(layout.contentX(), y, layout.contentX() + layout.contentWidth(), y + BrowserPanelLayout.LIST_ROW, 0x804F72A5);
                hoveredEntry = entry;
            }
            graphics.renderItem(entry.representative(), layout.contentX() + 1, y + 1);
            String count = InventoryCountFormatter.item(entry, capacity);
            int countWidth = minecraft.font.width(count);
            graphics.drawString(minecraft.font, minecraft.font.plainSubstrByWidth(entry.representative().getHoverName().getString(),
                    Math.max(12, layout.contentWidth() - 26 - countWidth)), layout.contentX() + 21, y + 5, 0xFFFFFF, false);
            graphics.drawString(minecraft.font, count, layout.contentX() + layout.contentWidth() - countWidth - 2, y + 5, 0xDDDDDD, false);
        }
    }

    private static void renderHandle(GuiGraphics graphics, int mouseX, int mouseY) {
        boolean hovered = inside(mouseX, mouseY, handleX, handleY,
                BrowserPanelLayout.HANDLE_WIDTH, BrowserPanelLayout.HANDLE_HEIGHT);
        graphics.fill(handleX, handleY, handleX + BrowserPanelLayout.HANDLE_WIDTH,
                handleY + BrowserPanelLayout.HANDLE_HEIGHT, hovered ? 0xFFF0F0F0 : 0xFFC6C6C6);
        graphics.fill(handleX + 2, handleY + 2, handleX + BrowserPanelLayout.HANDLE_WIDTH - 2,
                handleY + BrowserPanelLayout.HANDLE_HEIGHT - 2,
                open ? 0xFF356DA5 : hovered ? 0xFF777777 : 0xFF555555);
        String configuredHandleIcon = ClientConfig.BROWSER_HANDLE_ICON.get();
        if (BUILT_IN_LOGO_ICON.equals(configuredHandleIcon)) {
            graphics.blit(LOGO_TEXTURE, handleX + 2, handleY + 1, 16, 16,
                    0, 0, 256, 256, 256, 256);
        } else {
            graphics.renderItem(configuredIcon(configuredHandleIcon), handleX + 2, handleY + 1);
        }
        if (dragging && ClientConfig.AUTO_BROWSER_DOCK_SIDE.getAsBoolean()) {
            graphics.pose().pushPose();
            graphics.pose().translate(0, 0, 300);
            graphics.drawCenteredString(Minecraft.getInstance().font, arrow(dockSide),
                    handleX + BrowserPanelLayout.HANDLE_WIDTH / 2, handleY + 5, 0xFFFFFF);
            graphics.pose().popPose();
        }
        if (hovered) hoveredControl = "gui.stacksnotslots.inventory_browser_drag";
    }

    private static void renderSquare(GuiGraphics graphics, BrowserPanelLayout.ButtonBounds bounds,
                                     ItemStack icon, int mouseX, int mouseY, String tooltip) {
        int x = bounds.x();
        int y = bounds.y();
        boolean hovered = bounds.contains(mouseX, mouseY);
        graphics.fill(x, y, x + BrowserPanelLayout.CONTROL_WIDTH, y + BrowserPanelLayout.CONTROL_HEIGHT,
                hovered ? 0xFF7A7A7A : 0xFF555555);
        graphics.renderItem(icon, x + 2, y + 1);
        if (hovered && hoveredControl == null) hoveredControl = tooltip;
    }

    private static void renderTextSquare(GuiGraphics graphics, BrowserPanelLayout.ButtonBounds bounds,
                                         String text, int mouseX, int mouseY, String tooltip) {
        int x = bounds.x();
        int y = bounds.y();
        boolean hovered = bounds.contains(mouseX, mouseY);
        graphics.fill(x, y, x + BrowserPanelLayout.CONTROL_WIDTH, y + BrowserPanelLayout.CONTROL_HEIGHT,
                hovered ? 0xFF7A7A7A : 0xFF555555);
        graphics.drawCenteredString(Minecraft.getInstance().font, text,
                x + BrowserPanelLayout.CONTROL_WIDTH / 2, y + 5, 0xFFFFFF);
        if (hovered) hoveredControl = tooltip;
    }

    private static LogicalInventoryEntry entryAt(AbstractContainerScreen<?> screen, double mouseX, double mouseY) {
        BrowserPanelLayout layout = layout(screen);
        if (!inside(mouseX, mouseY, layout.contentX(), layout.contentY(), layout.contentWidth(), layout.contentHeight())) return null;
        int offset;
        if (ClientConfig.BROWSER_VIEW_MODE.get() == ClientConfig.BrowserViewMode.GRID) {
            int column = ((int)mouseX - layout.contentX()) / BrowserPanelLayout.GRID_CELL;
            int row = ((int)mouseY - layout.contentY()) / BrowserPanelLayout.GRID_CELL;
            if (column >= layout.gridColumns() || row >= layout.gridRows()) return null;
            offset = row * layout.gridColumns() + column;
        } else offset = ((int)mouseY - layout.contentY()) / BrowserPanelLayout.LIST_ROW;
        List<LogicalInventoryEntry> entries = entries();
        int index = scroll + offset;
        return index >= 0 && index < entries.size() ? entries.get(index) : null;
    }

    private static List<LogicalInventoryEntry> entries() {
        var player = Minecraft.getInstance().player;
        if (player == null) return List.of();
        var data = player.getData(ModAttachments.PLAYER_DATA);
        ResourceLocation categoryId = data.selectedCategoryPreference();
        SortMode sortMode = data.inventorySortPreference();
        String query = SEARCH.value();
        if (player.getUUID().equals(cachedPlayer) && data.inventory().revision() == cachedRevision
                && query.equals(cachedQuery) && java.util.Objects.equals(categoryId, cachedCategory) && sortMode == cachedSort) return cachedEntries;
        cachedPlayer = player.getUUID();
        cachedRevision = data.inventory().revision();
        cachedQuery = query;
        cachedCategory = categoryId;
        cachedSort = sortMode;
        CategoryDefinition category = currentCategory();
        boolean searching = !query.trim().isEmpty();
        ItemSearchExpression search = ItemSearchExpression.parse(query);
        ArrayList<LogicalInventoryEntry> values = new ArrayList<>();
        for (LogicalInventoryEntry entry : data.inventory().entriesAtOrAfter(36)) {
            if (!searching && category != null && !CategoryMatcher.matches(category, entry.representative())) continue;
            if (search.matches(entry.representative(), () -> ClientTooltipSearchIndex.text(entry.representative()))) {
                values.add(entry);
            }
        }
        values.sort(comparator(sortMode));
        cachedEntries = List.copyOf(values);
        scroll = Math.min(scroll, Math.max(0, cachedEntries.size() - 1));
        return cachedEntries;
    }

    private static Comparator<LogicalInventoryEntry> comparator(SortMode mode) {
        Comparator<LogicalInventoryEntry> byName = Comparator.comparing(entry -> entry.representative().getHoverName().getString(), String.CASE_INSENSITIVE_ORDER);
        Comparator<LogicalInventoryEntry> byQuantity = Comparator.comparingLong(LogicalInventoryEntry::quantity);
        Comparator<LogicalInventoryEntry> byId = Comparator.comparing(entry -> BuiltInRegistries.ITEM.getKey(entry.representative().getItem()).toString());
        return switch (mode) {
            case NAME_ASCENDING -> byName;
            case NAME_DESCENDING -> byName.reversed();
            case QUANTITY_ASCENDING -> byQuantity.thenComparing(byName);
            case QUANTITY_DESCENDING -> byQuantity.reversed().thenComparing(byName);
            case REGISTRY_ID -> byId;
            case MOD_NAMESPACE -> Comparator.comparing((LogicalInventoryEntry entry) ->
                    BuiltInRegistries.ITEM.getKey(entry.representative().getItem()).getNamespace()).thenComparing(byId);
        };
    }

    private static CategoryDefinition currentCategory() {
        var player = Minecraft.getInstance().player;
        if (player == null) return null;
        var data = player.getData(ModAttachments.PLAYER_DATA);
        CategoryDefinition selected = data.selectedCategoryPreference() == null ? null : data.categories().find(data.selectedCategoryPreference());
        if (selected != null && selected.enabled()) return selected;
        return data.categories().categories().stream().filter(CategoryDefinition::enabled).findFirst().orElse(null);
    }

    private static SortMode currentSort() {
        return Minecraft.getInstance().player.getData(ModAttachments.PLAYER_DATA).inventorySortPreference();
    }

    private static void changeCategory(int direction) {
        var data = Minecraft.getInstance().player.getData(ModAttachments.PLAYER_DATA);
        List<CategoryDefinition> categories = data.categories().categories().stream().filter(CategoryDefinition::enabled).toList();
        if (categories.isEmpty()) return;
        int current = 0;
        if (data.selectedCategoryPreference() != null) {
            for (int index = 0; index < categories.size(); index++) if (categories.get(index).id().equals(data.selectedCategoryPreference())) current = index;
        }
        CategoryDefinition category = categories.get(Math.floorMod(current + direction, categories.size()));
        data.setSelectedCategoryPreference(category.id());
        data.setInventorySortPreference(category.sortMode());
        PacketDistributor.sendToServer(new InventoryViewPreferencesPayload(category.sortMode(), category.id()));
        scroll = 0;
        invalidateEntries();
    }

    private static void cycleSort() {
        var data = Minecraft.getInstance().player.getData(ModAttachments.PLAYER_DATA);
        SortMode[] modes = SortMode.values();
        SortMode next = modes[(data.inventorySortPreference().ordinal() + 1) % modes.length];
        data.setInventorySortPreference(next);
        PacketDistributor.sendToServer(new InventoryViewPreferencesPayload(next, data.selectedCategoryPreference()));
        scroll = 0;
        invalidateEntries();
    }

    private static void cycleDockSide(Screen screen) {
        ClientConfig.BrowserDockSide[] sides = ClientConfig.BrowserDockSide.values();
        dockSide = sides[(dockSide.ordinal() + 1) % sides.length];
        constrainHandle(screen);
        persistScreenState(screen);
    }

    private static void chooseAutomaticSide(Screen screen) {
        if (!ClientConfig.AUTO_BROWSER_DOCK_SIDE.getAsBoolean()) return;
        double dx = handleX + BrowserPanelLayout.HANDLE_WIDTH / 2.0 - screen.width / 2.0;
        double dy = handleY + BrowserPanelLayout.HANDLE_HEIGHT / 2.0 - screen.height / 2.0;
        int deadX = ClientConfig.AUTO_DOCK_DEAD_ZONE_X.getAsInt();
        int deadY = ClientConfig.AUTO_DOCK_DEAD_ZONE_Y.getAsInt();
        if (Math.abs(dx) <= deadX && Math.abs(dy) <= deadY) return;
        double horizontal = Math.max(0, Math.abs(dx) - deadX);
        double vertical = Math.max(0, Math.abs(dy) - deadY);
        ClientConfig.BrowserDockSide side = horizontal >= vertical
                ? dx < 0 ? ClientConfig.BrowserDockSide.LEFT : ClientConfig.BrowserDockSide.RIGHT
                : dy < 0 ? ClientConfig.BrowserDockSide.TOP : ClientConfig.BrowserDockSide.BOTTOM;
        dockSide = side;
    }

    private static void setOpen(boolean value, Screen screen) {
        open = value;
        PacketDistributor.sendToServer(new BrowserStatePayload(value));
        persistScreenState(screen);
    }

    private static void beginNewSearch(Screen screen) {
        visible = true;
        searchFocused = true;
        scroll = 0;
        updateSearch(SEARCH.clear());
        clearPointerCapture();
        if (!open) setOpen(true, screen);
        else persistScreenState(screen);
    }

    private static void invalidateEntries() { cachedRevision = -1; }

    private static void updateSearch(boolean changed) {
        if (!changed) return;
        scroll = 0;
        invalidateEntries();
    }

    private static void capturePointer(Screen screen, int button) {
        capturedPressScreen = screen;
        consumedReleaseButton = button;
    }

    private static void clearPointerCapture() {
        capturedPressScreen = null;
        consumedReleaseButton = -1;
        handlePressed = false;
        dragging = false;
    }

    private static void updateDrag(Screen screen, double mouseX, double mouseY) {
        if (!handlePressed || capturedPressScreen != screen) return;
        if (!dragging && !exceedsDragThreshold(pressX, pressY, mouseX, mouseY)) return;
        dragging = true;
        handleX = (int)Math.round(mouseX - dragOffsetX);
        handleY = (int)Math.round(mouseY - dragOffsetY);
        chooseAutomaticSide(screen);
        constrainHandle(screen);
    }

    static boolean exceedsDragThreshold(double startX, double startY, double currentX, double currentY) {
        return Math.hypot(currentX - startX, currentY - startY) >= DRAG_THRESHOLD;
    }

    private static double scaledMouseX(Minecraft minecraft) {
        return minecraft.mouseHandler.xpos() * minecraft.getWindow().getGuiScaledWidth()
                / minecraft.getWindow().getScreenWidth();
    }

    private static double scaledMouseY(Minecraft minecraft) {
        return minecraft.mouseHandler.ypos() * minecraft.getWindow().getGuiScaledHeight()
                / minecraft.getWindow().getScreenHeight();
    }

    private static ItemStack configuredIcon(String id) {
        ResourceLocation location = ResourceLocation.tryParse(id);
        return location == null ? ItemStack.EMPTY : BuiltInRegistries.ITEM.getOptional(location)
                .map(Item::getDefaultInstance).orElse(ItemStack.EMPTY);
    }

    private static void migrateLegacyHandleIconDefault() {
        if (ClientConfig.BROWSER_HANDLE_ICON_MIGRATED.getAsBoolean()) return;
        if ("minecraft:spyglass".equals(ClientConfig.BROWSER_HANDLE_ICON.get())) {
            ClientConfig.BROWSER_HANDLE_ICON.set(BUILT_IN_LOGO_ICON);
        }
        ClientConfig.BROWSER_HANDLE_ICON_MIGRATED.set(true);
        ClientSaveState.saveClientSettings();
    }

    private static String shortSortName(SortMode mode) {
        return switch (mode) {
            case NAME_ASCENDING -> "A-Z";
            case NAME_DESCENDING -> "Z-A";
            case QUANTITY_ASCENDING -> "1-9";
            case QUANTITY_DESCENDING -> "9-1";
            case REGISTRY_ID -> "ID";
            case MOD_NAMESPACE -> "@";
        };
    }

    private static String arrow(ClientConfig.BrowserDockSide side) {
        return switch (side) {
            case LEFT -> "<";
            case RIGHT -> ">";
            case TOP -> "^";
            case BOTTOM -> "v";
        };
    }

    private static BrowserPanelLayout layout(AbstractContainerScreen<?> screen) {
        return BrowserPanelLayout.calculate(screen.width, screen.height, handleX, handleY, dockSide,
                ClientConfig.BROWSER_VIEW_MODE.get(), ClientConfig.BROWSER_GRID_COLUMNS.getAsInt(),
                ClientConfig.BROWSER_GRID_ROWS.getAsInt());
    }

    private static Rect2i panelBounds(AbstractContainerScreen<?> screen) {
        BrowserPanelLayout layout = layout(screen);
        return new Rect2i(layout.panelX(), layout.panelY(), layout.panelWidth(), layout.panelHeight());
    }

    private static void ensurePosition(Screen screen) {
        AbstractContainerScreen<?> container = (AbstractContainerScreen<?>)screen;
        int guiLeft = container.getGuiLeft();
        int guiTop = container.getGuiTop();
        if (lastScreen != screen) {
            clearPointerCapture();
            lastScreen = screen;
            activeScreenType = screenStateKey(container);
            BrowserScreenStateStore.State state = BrowserScreenStateStore.load(activeScreenType);
            anchoredGuiLeft = guiLeft;
            anchoredGuiTop = guiTop;
            open = state.open();
            visible = state.visible();
            dockSide = state.dockSide();
            searchFocused = false;
            SEARCH.clearSelection();
            if (state.hasPosition()) {
                handleX = guiLeft + state.offsetX();
                handleY = guiTop + state.offsetY();
            } else {
                BrowserDefaultPosition defaultPosition = BrowserDefaultPosition.resolve(
                        guiLeft, guiTop, container.getXSize(), container.getYSize(),
                        ClientConfig.BROWSER_DEFAULT_PLACEMENT.get());
                handleX = defaultPosition.x();
                handleY = defaultPosition.y();
            }
            constrainHandle(screen);
        } else if (guiLeft != anchoredGuiLeft || guiTop != anchoredGuiTop) {
            // Keep the handle attached to the same local GUI position when a mod or resize moves the menu.
            handleX += guiLeft - anchoredGuiLeft;
            handleY += guiTop - anchoredGuiTop;
            anchoredGuiLeft = guiLeft;
            anchoredGuiTop = guiTop;
            constrainHandle(screen);
        }
    }

    private static void constrainHandle(Screen screen) {
        BrowserPanelLayout.HandlePosition constrained = BrowserPanelLayout.constrainHandle(
                screen.width, screen.height, handleX, handleY, dockSide);
        handleX = constrained.x();
        handleY = constrained.y();
    }

    private static void persistScreenState(Screen screen) {
        if (!supports(screen) || activeScreenType == null) return;
        AbstractContainerScreen<?> container = (AbstractContainerScreen<?>)screen;
        BrowserScreenStateStore.save(new BrowserScreenStateStore.State(
                activeScreenType, handleX - container.getGuiLeft(), handleY - container.getGuiTop(),
                open, visible, dockSide));
    }

    /** Class identity and dimensions distinguish UI types without assuming every menu exposes a constructible type. */
    private static String screenStateKey(AbstractContainerScreen<?> screen) {
        return screenStateKey(screen.getClass().getName(), screen.getMenu().getClass().getName(),
                screen.getXSize(), screen.getYSize());
    }

    static String screenStateKey(String screenClass, String menuClass, int width, int height) {
        return screenClass + '#' + menuClass + '#' + width + 'x' + height;
    }

    private static boolean supports(Screen screen) { return screen instanceof AbstractContainerScreen<?>; }
    private static boolean inside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }
    private static int clamp(int value, int minimum, int maximum) { return Math.max(minimum, Math.min(maximum, value)); }

}
