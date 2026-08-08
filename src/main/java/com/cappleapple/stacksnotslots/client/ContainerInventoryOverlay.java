package com.cappleapple.stacksnotslots.client;

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
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/** A topmost logical-inventory browser whose native listener owns only its visible bounds. */
public final class ContainerInventoryOverlay {
    public static final int CONTROL_WIDTH = 20;
    public static final int CONTROL_HEIGHT = 18;
    private static final int HANDLE_WIDTH = 20;
    private static final int HANDLE_HEIGHT = 18;
    private static final int GRID_CELL = 20;
    private static final int LIST_ROW = 18;
    private static final int MAX_QUERY = 96;
    private static Screen lastScreen;
    private static Screen stateSyncedScreen;
    private static int handleX;
    private static int handleY;
    private static boolean initializedPosition;
    private static boolean open;
    private static boolean searchFocused;
    private static boolean dragging;
    private static int consumedReleaseButton = -1;
    private static double dragOffsetX;
    private static double dragOffsetY;
    private static double pressX;
    private static double pressY;
    private static int scroll;
    private static String query = "";
    private static UUID cachedPlayer;
    private static long cachedRevision = -1;
    private static String cachedQuery = "";
    private static ResourceLocation cachedCategory;
    private static SortMode cachedSort;
    private static List<LogicalInventoryEntry> cachedEntries = List.of();
    private static final Map<SearchIdentity, String> tooltipIndex = new HashMap<>();
    private static LogicalInventoryEntry hoveredEntry;
    private static CategoryDefinition hoveredCategory;
    private static String hoveredControl;

    private ContainerInventoryOverlay() {}

    /** Keeps server browser state in sync without depending on a screen's child-input implementation. */
    public static void initialize(ScreenEvent.Init.Pre event) {
        if (!supports(event.getScreen())) return;
        if (stateSyncedScreen != event.getScreen()) {
            stateSyncedScreen = event.getScreen();
            PacketDistributor.sendToServer(new BrowserStatePayload(isOpen()));
        }
    }

    /**
     * Routes input before any concrete container screen sees it. Modded screens are not required to
     * dispatch through {@link net.minecraft.client.gui.components.events.ContainerEventHandler}, so
     * registering this overlay as a child widget is not universal. Only exact overlay bounds (or the
     * explicit control-click stow gesture) are cancelled here.
     */
    public static void click(ScreenEvent.MouseButtonPressed.Pre event) {
        if (!supports(event.getScreen()) || Minecraft.getInstance().player == null) return;
        AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>)event.getScreen();
        if (mouseClicked(screen, event.getMouseX(), event.getMouseY(), event.getButton())) {
            event.setCanceled(true);
            return;
        }

        var slot = screen.getSlotUnderMouse();
        if (event.getButton() == 0 && Screen.hasControlDown() && slot != null && slot.container instanceof Inventory
                && slot.getContainerSlot() >= 0 && slot.getContainerSlot() < Inventory.INVENTORY_SIZE
                && !slot.getItem().isEmpty()) {
            consumedReleaseButton = event.getButton();
            PacketDistributor.sendToServer(new StowSlotPayload(slot.getContainerSlot()));
            event.setCanceled(true);
        }
    }

    /** Cancels scrolling only when a browser control actually handled it. */
    public static void scroll(ScreenEvent.MouseScrolled.Pre event) {
        if (!supports(event.getScreen())) return;
        if (mouseScrolled((AbstractContainerScreen<?>)event.getScreen(), event.getMouseX(), event.getMouseY(), event.getScrollDeltaY())) {
            event.setCanceled(true);
        }
    }

    public static void render(ScreenEvent.Render.Post event) {
        if (!supports(event.getScreen()) || Minecraft.getInstance().player == null) return;
        ensurePosition(event.getScreen());
        if (!ClientConfig.BROWSER_HANDLE_VISIBLE.getAsBoolean()) return;
        GuiGraphics graphics = event.getGuiGraphics();
        hoveredEntry = null;
        hoveredCategory = null;
        hoveredControl = null;
        if (open) renderPanel((AbstractContainerScreen<?>)event.getScreen(), graphics, event.getMouseX(), event.getMouseY());
        renderHandle(graphics, event.getMouseX(), event.getMouseY());
        if (hoveredEntry != null) graphics.renderTooltip(Minecraft.getInstance().font, hoveredEntry.representative(), event.getMouseX(), event.getMouseY());
        else if (hoveredCategory != null) graphics.renderTooltip(Minecraft.getInstance().font, Component.literal(hoveredCategory.displayName()), event.getMouseX(), event.getMouseY());
        else if (hoveredControl != null) graphics.renderTooltip(Minecraft.getInstance().font, Component.translatable(hoveredControl), event.getMouseX(), event.getMouseY());
    }

    /** Scoped pre-event support for an overlay press; unrelated modded-screen input is never cancelled. */
    public static void drag(ScreenEvent.MouseDragged.Pre event) {
        if (!dragging || event.getMouseButton() != 0 || !supports(event.getScreen())) return;
        Screen screen = event.getScreen();
        handleX = clamp((int)Math.round(event.getMouseX() - dragOffsetX), 0, screen.width - HANDLE_WIDTH);
        handleY = clamp((int)Math.round(event.getMouseY() - dragOffsetY), 0, screen.height - HANDLE_HEIGHT);
        chooseAutomaticSide(screen);
        event.setCanceled(true);
    }

    /** Cancels only the release paired with a browser-owned press, avoiding click-through. */
    public static void release(ScreenEvent.MouseButtonReleased.Pre event) {
        if (event.getButton() != consumedReleaseButton) return;
        consumedReleaseButton = -1;
        if (dragging && event.getButton() == 0) {
            dragging = false;
            if (Math.hypot(event.getMouseX() - pressX, event.getMouseY() - pressY) < 3.0) setOpen(!open, event.getScreen());
            ClientConfig.BROWSER_HANDLE_X.set(handleX);
            ClientConfig.BROWSER_HANDLE_Y.set(handleY);
            refreshIngredientLayout(event.getScreen());
        }
        event.setCanceled(true);
    }

    public static void keyPressed(ScreenEvent.KeyPressed.Pre event) {
        if (!supports(event.getScreen())) return;
        if (ClientKeyMappings.TOGGLE_BROWSER.isActiveAndMatches(InputConstants.getKey(event.getKeyCode(), event.getScanCode()))) {
            if (open) {
                setOpen(false, event.getScreen());
                ClientConfig.BROWSER_HANDLE_VISIBLE.set(false);
            } else {
                ClientConfig.BROWSER_HANDLE_VISIBLE.set(!ClientConfig.BROWSER_HANDLE_VISIBLE.getAsBoolean());
                refreshIngredientLayout(event.getScreen());
            }
            searchFocused = false;
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
        switch (event.getKeyCode()) {
            case GLFW.GLFW_KEY_ESCAPE -> searchFocused = false;
            case GLFW.GLFW_KEY_BACKSPACE -> {
                if (!query.isEmpty()) query = query.substring(0, query.length() - 1);
                invalidateEntries();
            }
            case GLFW.GLFW_KEY_DELETE -> { query = ""; invalidateEntries(); }
            default -> { return; }
        }
        event.setCanceled(true);
    }

    public static void characterTyped(ScreenEvent.CharacterTyped.Pre event) {
        if (!supports(event.getScreen()) || !open || !searchFocused || query.length() >= MAX_QUERY) return;
        char character = event.getCodePoint();
        if (character >= 32 && character != 127) {
            query += character;
            invalidateEntries();
            event.setCanceled(true);
        }
    }

    public static void focusSearch() {
        ClientConfig.BROWSER_HANDLE_VISIBLE.set(true);
        setOpen(true, Minecraft.getInstance().screen);
        searchFocused = true;
    }

    public static boolean isOpen() { return open && ClientConfig.BROWSER_HANDLE_VISIBLE.getAsBoolean(); }

    public static boolean isDragging() { return dragging; }

    /** Exact topmost input region, used by the custom inventory selector to avoid competing when overlapped. */
    public static boolean ownsPoint(Screen screen, double mouseX, double mouseY) {
        if (!supports(screen) || !ClientConfig.BROWSER_HANDLE_VISIBLE.getAsBoolean()) return false;
        ensurePosition(screen);
        if (inside(mouseX, mouseY, handleX, handleY, HANDLE_WIDTH, HANDLE_HEIGHT)) return true;
        if (!open) return false;
        Rect2i panel = panelBounds((AbstractContainerScreen<?>)screen);
        return inside(mouseX, mouseY, panel.getX(), panel.getY(), panel.getWidth(), panel.getHeight());
    }

    public static Rect2i currentBounds(Screen screen) {
        if (!supports(screen)) return new Rect2i(0, 0, 0, 0);
        ensurePosition(screen);
        if (!ClientConfig.BROWSER_HANDLE_VISIBLE.getAsBoolean()) return new Rect2i(0, 0, 0, 0);
        if (!open) return new Rect2i(handleX, handleY, HANDLE_WIDTH, HANDLE_HEIGHT);
        Rect2i panel = panelBounds((AbstractContainerScreen<?>)screen);
        int left = Math.min(handleX, panel.getX());
        int top = Math.min(handleY, panel.getY());
        int right = Math.max(handleX + HANDLE_WIDTH, panel.getX() + panel.getWidth());
        int bottom = Math.max(handleY + HANDLE_HEIGHT, panel.getY() + panel.getHeight());
        return new Rect2i(left, top, right - left, bottom - top);
    }

    /** Exact dynamic areas so ingredient overlays wrap each floating element instead of reserving the gap between them. */
    public static List<Rect2i> currentAreas(Screen screen) {
        if (!supports(screen)) return List.of();
        ensurePosition(screen);
        if (!ClientConfig.BROWSER_HANDLE_VISIBLE.getAsBoolean()) return List.of();
        Rect2i handle = new Rect2i(handleX, handleY, HANDLE_WIDTH, HANDLE_HEIGHT);
        if (!open) return List.of(handle);
        return List.of(handle, panelBounds((AbstractContainerScreen<?>)screen));
    }

    private static boolean mouseClicked(AbstractContainerScreen<?> screen, double mouseX, double mouseY, int button) {
        ensurePosition(screen);
        boolean handleVisible = ClientConfig.BROWSER_HANDLE_VISIBLE.getAsBoolean();
        if (handleVisible && inside(mouseX, mouseY, handleX, handleY, HANDLE_WIDTH, HANDLE_HEIGHT)) {
            consumedReleaseButton = button;
            if (button == 0) {
                dragging = true;
                dragOffsetX = mouseX - handleX;
                dragOffsetY = mouseY - handleY;
                pressX = mouseX;
                pressY = mouseY;
            }
            return true;
        }
        if (!handleVisible || !open) return false;
        PanelLayout layout = layout(screen);
        if (!inside(mouseX, mouseY, layout.panelX, layout.panelY, layout.panelWidth, layout.panelHeight)) {
            searchFocused = false;
            return false;
        }

        consumedReleaseButton = button;
        if (inside(mouseX, mouseY, layout.searchX, layout.searchY, layout.searchWidth, CONTROL_HEIGHT)) {
            searchFocused = true;
            return true;
        }
        searchFocused = false;
        if (inside(mouseX, mouseY, layout.categoryX, layout.controlsY, CONTROL_WIDTH, CONTROL_HEIGHT)) {
            changeCategory(button == 1 ? -1 : 1);
            return true;
        }
        if (inside(mouseX, mouseY, layout.sortX, layout.controlsY, CONTROL_WIDTH, CONTROL_HEIGHT)) {
            cycleSort();
            return true;
        }
        if (inside(mouseX, mouseY, layout.transferX, layout.controlsY, CONTROL_WIDTH, CONTROL_HEIGHT)) {
            PacketDistributor.sendToServer(new BulkTransferPayload(Screen.hasShiftDown()
                    ? BulkTransferPayload.Direction.TO_CONTAINER : BulkTransferPayload.Direction.FROM_CONTAINER,
                    BulkTransferPayload.Target.OPEN_MENU));
            return true;
        }
        if (inside(mouseX, mouseY, layout.directionX, layout.bottomButtonY, CONTROL_WIDTH, CONTROL_HEIGHT)) {
            cycleDockSide(screen);
            return true;
        }
        if (inside(mouseX, mouseY, layout.manageX, layout.bottomButtonY, CONTROL_WIDTH, CONTROL_HEIGHT)) {
            Minecraft.getInstance().setScreen(new CategoryManagerScreen(screen, Minecraft.getInstance().player));
            return true;
        }
        if (inside(mouseX, mouseY, layout.settingsX, layout.bottomButtonY, CONTROL_WIDTH, CONTROL_HEIGHT)) {
            Minecraft.getInstance().setScreen(new InventoryBrowserSettingsScreen(screen));
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
        if (!open || !ClientConfig.BROWSER_HANDLE_VISIBLE.getAsBoolean()) return false;
        PanelLayout layout = layout(screen);
        if (inside(mouseX, mouseY, layout.categoryX, layout.controlsY, CONTROL_WIDTH, CONTROL_HEIGHT)) {
            changeCategory(deltaY > 0 ? -1 : 1);
            return true;
        }
        if (!inside(mouseX, mouseY, layout.contentX, layout.contentY, layout.contentWidth, layout.contentHeight)) return false;
        int page = ClientConfig.BROWSER_VIEW_MODE.get() == ClientConfig.BrowserViewMode.GRID
                ? ClientConfig.BROWSER_GRID_COLUMNS.getAsInt() : 1;
        int maximum = Math.max(0, entries().size() - layout.visibleEntryCount);
        scroll = clamp(scroll - (int)Math.signum(deltaY) * page, 0, maximum);
        return true;
    }

    private static void renderPanel(AbstractContainerScreen<?> screen, GuiGraphics graphics, int mouseX, int mouseY) {
        PanelLayout layout = layout(screen);
        Minecraft minecraft = Minecraft.getInstance();
        var inventory = minecraft.player.getData(ModAttachments.PLAYER_DATA).inventory();
        List<LogicalInventoryEntry> entries = entries();
        scroll = Math.min(scroll, Math.max(0, entries.size() - layout.visibleEntryCount));

        graphics.fill(layout.panelX, layout.panelY, layout.panelX + layout.panelWidth, layout.panelY + layout.panelHeight, 0xF0C6C6C6);
        graphics.fill(layout.panelX + 2, layout.panelY + 2, layout.panelX + layout.panelWidth - 2, layout.panelY + layout.panelHeight - 2, 0xF0202020);
        graphics.fill(layout.searchX, layout.searchY, layout.searchX + layout.searchWidth, layout.searchY + CONTROL_HEIGHT,
                searchFocused ? 0xFF101010 : 0xFF282828);
        String shownQuery = query.isEmpty() && !searchFocused ? Component.translatable("gui.stacksnotslots.search_hint_compact").getString() : query;
        graphics.drawString(minecraft.font, minecraft.font.plainSubstrByWidth(shownQuery, layout.searchWidth - 7),
                layout.searchX + 4, layout.searchY + 5, query.isEmpty() ? 0x888888 : 0xFFFFFF, false);
        if (searchFocused && (System.currentTimeMillis() / 500L) % 2 == 0) {
            int cursorX = layout.searchX + 4 + minecraft.font.width(minecraft.font.plainSubstrByWidth(query, layout.searchWidth - 7));
            graphics.fill(cursorX, layout.searchY + 3, cursorX + 1, layout.searchY + 15, 0xFFFFFFFF);
        }

        renderSquare(graphics, layout.categoryX, layout.controlsY, CategoryIcons.displayStack(currentCategory()), mouseX, mouseY,
                "gui.stacksnotslots.category_control");
        renderTextSquare(graphics, layout.sortX, layout.controlsY, shortSortName(currentSort()), mouseX, mouseY,
                "gui.stacksnotslots.sort_control");
        renderSquare(graphics, layout.transferX, layout.controlsY,
                new ItemStack(Screen.hasShiftDown() ? Items.PISTON : Items.STICKY_PISTON), mouseX, mouseY,
                Screen.hasShiftDown() ? "gui.stacksnotslots.dump_to_container" : "gui.stacksnotslots.extract_from_container");
        if (inside(mouseX, mouseY, layout.categoryX, layout.controlsY, CONTROL_WIDTH, CONTROL_HEIGHT)) hoveredCategory = currentCategory();

        if (ClientConfig.BROWSER_VIEW_MODE.get() == ClientConfig.BrowserViewMode.GRID) renderGrid(graphics, layout, entries, mouseX, mouseY);
        else renderList(graphics, layout, entries, mouseX, mouseY);

        long used = inventory.usedCapacity();
        long capacity = inventory.capacity();
        graphics.drawString(minecraft.font, InventoryCountFormatter.overall(used, capacity), layout.panelX + 5,
                layout.capacityTextY, used > capacity ? 0xFF7777 : 0xFFFFFF, false);
        int barWidth = layout.panelWidth - 10;
        int filled = capacity <= 0 ? (used > 0 ? barWidth : 0) : used >= capacity ? barWidth : (int)(used * barWidth / capacity);
        graphics.fill(layout.panelX + 5, layout.capacityBarY, layout.panelX + 5 + barWidth, layout.capacityBarY + 5, 0xFF454545);
        graphics.fill(layout.panelX + 5, layout.capacityBarY, layout.panelX + 5 + filled, layout.capacityBarY + 5,
                used > capacity ? 0xFFE34B4B : 0xFF54B45A);
        renderTextSquare(graphics, layout.directionX, layout.bottomButtonY, arrow(ClientConfig.BROWSER_DOCK_SIDE.get()), mouseX, mouseY,
                "gui.stacksnotslots.browser_direction");
        renderSquare(graphics, layout.manageX, layout.bottomButtonY, configuredIcon(ClientConfig.MANAGE_TABS_ICON.get()), mouseX, mouseY,
                "gui.stacksnotslots.manage_tabs");
        renderSquare(graphics, layout.settingsX, layout.bottomButtonY, configuredIcon(ClientConfig.SETTINGS_ICON.get()), mouseX, mouseY,
                "gui.stacksnotslots.settings");
    }

    private static void renderGrid(GuiGraphics graphics, PanelLayout layout, List<LogicalInventoryEntry> entries, int mouseX, int mouseY) {
        Minecraft minecraft = Minecraft.getInstance();
        int columns = ClientConfig.BROWSER_GRID_COLUMNS.getAsInt();
        long capacity = minecraft.player.getData(ModAttachments.PLAYER_DATA).inventory().capacity();
        for (int offset = 0; offset < layout.visibleEntryCount; offset++) {
            int index = scroll + offset;
            if (index >= entries.size()) break;
            LogicalInventoryEntry entry = entries.get(index);
            int x = layout.contentX + offset % columns * GRID_CELL;
            int y = layout.contentY + offset / columns * GRID_CELL;
            if (inside(mouseX, mouseY, x, y, GRID_CELL, GRID_CELL)) {
                graphics.fill(x, y, x + GRID_CELL, y + GRID_CELL, 0x804F72A5);
                hoveredEntry = entry;
            }
            graphics.renderItem(entry.representative(), x + 2, y + 2);
            String count = InventoryCountFormatter.item(entry, capacity);
            graphics.pose().pushPose();
            graphics.pose().translate(0, 0, 250);
            graphics.pose().scale(0.5F, 0.5F, 1.0F);
            graphics.drawString(minecraft.font, count,
                    (x + GRID_CELL - 1) * 2 - minecraft.font.width(count), (y + 12) * 2, 0xFFFFFF, true);
            graphics.pose().popPose();
        }
    }

    private static void renderList(GuiGraphics graphics, PanelLayout layout, List<LogicalInventoryEntry> entries, int mouseX, int mouseY) {
        Minecraft minecraft = Minecraft.getInstance();
        long capacity = minecraft.player.getData(ModAttachments.PLAYER_DATA).inventory().capacity();
        for (int row = 0; row < layout.visibleEntryCount; row++) {
            int index = scroll + row;
            if (index >= entries.size()) break;
            LogicalInventoryEntry entry = entries.get(index);
            int y = layout.contentY + row * LIST_ROW;
            if (inside(mouseX, mouseY, layout.contentX, y, layout.contentWidth, LIST_ROW)) {
                graphics.fill(layout.contentX, y, layout.contentX + layout.contentWidth, y + LIST_ROW, 0x804F72A5);
                hoveredEntry = entry;
            }
            graphics.renderItem(entry.representative(), layout.contentX + 1, y + 1);
            String count = InventoryCountFormatter.item(entry, capacity);
            int countWidth = minecraft.font.width(count);
            graphics.drawString(minecraft.font, minecraft.font.plainSubstrByWidth(entry.representative().getHoverName().getString(),
                    Math.max(12, layout.contentWidth - 26 - countWidth)), layout.contentX + 21, y + 5, 0xFFFFFF, false);
            graphics.drawString(minecraft.font, count, layout.contentX + layout.contentWidth - countWidth - 2, y + 5, 0xDDDDDD, false);
        }
    }

    private static void renderHandle(GuiGraphics graphics, int mouseX, int mouseY) {
        boolean hovered = inside(mouseX, mouseY, handleX, handleY, HANDLE_WIDTH, HANDLE_HEIGHT);
        graphics.fill(handleX, handleY, handleX + HANDLE_WIDTH, handleY + HANDLE_HEIGHT, hovered ? 0xFFF0F0F0 : 0xFFC6C6C6);
        graphics.fill(handleX + 2, handleY + 2, handleX + HANDLE_WIDTH - 2, handleY + HANDLE_HEIGHT - 2,
                open ? 0xFF356DA5 : hovered ? 0xFF777777 : 0xFF555555);
        graphics.renderItem(configuredIcon(ClientConfig.BROWSER_HANDLE_ICON.get()), handleX + 2, handleY + 1);
        if (dragging && ClientConfig.AUTO_BROWSER_DOCK_SIDE.getAsBoolean()) {
            graphics.pose().pushPose();
            graphics.pose().translate(0, 0, 300);
            graphics.drawCenteredString(Minecraft.getInstance().font, arrow(ClientConfig.BROWSER_DOCK_SIDE.get()),
                    handleX + HANDLE_WIDTH / 2, handleY + 5, 0xFFFFFF);
            graphics.pose().popPose();
        }
        if (hovered) hoveredControl = "gui.stacksnotslots.inventory_browser_drag";
    }

    private static void renderSquare(GuiGraphics graphics, int x, int y, ItemStack icon, int mouseX, int mouseY, String tooltip) {
        boolean hovered = inside(mouseX, mouseY, x, y, CONTROL_WIDTH, CONTROL_HEIGHT);
        graphics.fill(x, y, x + CONTROL_WIDTH, y + CONTROL_HEIGHT, hovered ? 0xFF7A7A7A : 0xFF555555);
        graphics.renderItem(icon, x + 2, y + 1);
        if (hovered && hoveredControl == null) hoveredControl = tooltip;
    }

    private static void renderTextSquare(GuiGraphics graphics, int x, int y, String text, int mouseX, int mouseY, String tooltip) {
        boolean hovered = inside(mouseX, mouseY, x, y, CONTROL_WIDTH, CONTROL_HEIGHT);
        graphics.fill(x, y, x + CONTROL_WIDTH, y + CONTROL_HEIGHT, hovered ? 0xFF7A7A7A : 0xFF555555);
        graphics.drawCenteredString(Minecraft.getInstance().font, text, x + CONTROL_WIDTH / 2, y + 5, 0xFFFFFF);
        if (hovered) hoveredControl = tooltip;
    }

    private static LogicalInventoryEntry entryAt(AbstractContainerScreen<?> screen, double mouseX, double mouseY) {
        PanelLayout layout = layout(screen);
        if (!inside(mouseX, mouseY, layout.contentX, layout.contentY, layout.contentWidth, layout.contentHeight)) return null;
        int offset;
        if (ClientConfig.BROWSER_VIEW_MODE.get() == ClientConfig.BrowserViewMode.GRID) {
            int column = ((int)mouseX - layout.contentX) / GRID_CELL;
            int row = ((int)mouseY - layout.contentY) / GRID_CELL;
            if (column >= ClientConfig.BROWSER_GRID_COLUMNS.getAsInt() || row >= layout.visibleEntryCount / ClientConfig.BROWSER_GRID_COLUMNS.getAsInt()) return null;
            offset = row * ClientConfig.BROWSER_GRID_COLUMNS.getAsInt() + column;
        } else offset = ((int)mouseY - layout.contentY) / LIST_ROW;
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
        if (player.getUUID().equals(cachedPlayer) && data.inventory().revision() == cachedRevision
                && query.equals(cachedQuery) && java.util.Objects.equals(categoryId, cachedCategory) && sortMode == cachedSort) return cachedEntries;
        cachedPlayer = player.getUUID();
        cachedRevision = data.inventory().revision();
        cachedQuery = query;
        cachedCategory = categoryId;
        cachedSort = sortMode;
        CategoryDefinition category = currentCategory();
        ArrayList<LogicalInventoryEntry> values = new ArrayList<>();
        for (LogicalInventoryEntry entry : data.inventory().entriesAtOrAfter(36)) {
            if (category != null && !CategoryMatcher.matches(category, entry.representative())) continue;
            if (matchesSearch(entry.representative(), query)) values.add(entry);
        }
        values.sort(comparator(sortMode));
        cachedEntries = List.copyOf(values);
        scroll = Math.min(scroll, Math.max(0, cachedEntries.size() - 1));
        return cachedEntries;
    }

    private static boolean matchesSearch(ItemStack stack, String raw) {
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) return true;
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (normalized.startsWith("@")) return id.getNamespace().contains(normalized.substring(1));
        if (normalized.startsWith("#")) return stack.getTags().anyMatch(tag -> tag.location().toString().contains(normalized.substring(1)));
        if (stack.getHoverName().getString().toLowerCase(Locale.ROOT).contains(normalized)
                || id.toString().contains(normalized) || id.getNamespace().contains(normalized)) return true;
        if (!ClientConfig.TOOLTIP_INDEXING.getAsBoolean()) return false;
        String tooltip = tooltipIndex.computeIfAbsent(new SearchIdentity(stack), ignored ->
                stack.getTooltipLines(Item.TooltipContext.of(Minecraft.getInstance().player.level()), Minecraft.getInstance().player,
                                net.minecraft.world.item.TooltipFlag.Default.NORMAL).stream().skip(1)
                        .map(Component::getString).map(line -> line.toLowerCase(Locale.ROOT)).reduce("", (left, right) -> left + '\n' + right));
        return tooltip.contains(normalized);
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
        ClientConfig.BrowserDockSide current = ClientConfig.BROWSER_DOCK_SIDE.get();
        ClientConfig.BrowserDockSide[] sides = ClientConfig.BrowserDockSide.values();
        ClientConfig.BROWSER_DOCK_SIDE.set(sides[(current.ordinal() + 1) % sides.length]);
        refreshIngredientLayout(screen);
    }

    private static void chooseAutomaticSide(Screen screen) {
        if (!ClientConfig.AUTO_BROWSER_DOCK_SIDE.getAsBoolean()) return;
        double dx = handleX + HANDLE_WIDTH / 2.0 - screen.width / 2.0;
        double dy = handleY + HANDLE_HEIGHT / 2.0 - screen.height / 2.0;
        int deadX = ClientConfig.AUTO_DOCK_DEAD_ZONE_X.getAsInt();
        int deadY = ClientConfig.AUTO_DOCK_DEAD_ZONE_Y.getAsInt();
        if (Math.abs(dx) <= deadX && Math.abs(dy) <= deadY) return;
        double horizontal = Math.max(0, Math.abs(dx) - deadX);
        double vertical = Math.max(0, Math.abs(dy) - deadY);
        ClientConfig.BrowserDockSide side = horizontal >= vertical
                ? dx < 0 ? ClientConfig.BrowserDockSide.LEFT : ClientConfig.BrowserDockSide.RIGHT
                : dy < 0 ? ClientConfig.BrowserDockSide.TOP : ClientConfig.BrowserDockSide.BOTTOM;
        if (side != ClientConfig.BROWSER_DOCK_SIDE.get()) ClientConfig.BROWSER_DOCK_SIDE.set(side);
    }

    private static void setOpen(boolean value, Screen screen) {
        open = value;
        PacketDistributor.sendToServer(new BrowserStatePayload(value));
        refreshIngredientLayout(screen);
    }

    /** Reinitialization makes JEI/EMI recompute exclusion geometry immediately after a layout change. */
    private static void refreshIngredientLayout(Screen screen) {
        Minecraft minecraft = Minecraft.getInstance();
        if (screen == null || minecraft.screen != screen) return;
        minecraft.execute(() -> {
            if (minecraft.screen == screen) screen.resize(minecraft, screen.width, screen.height);
        });
    }

    private static void invalidateEntries() { cachedRevision = -1; }

    private static ItemStack configuredIcon(String id) {
        ResourceLocation location = ResourceLocation.tryParse(id);
        return location == null ? ItemStack.EMPTY : BuiltInRegistries.ITEM.getOptional(location)
                .map(Item::getDefaultInstance).orElse(ItemStack.EMPTY);
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

    private static PanelLayout layout(AbstractContainerScreen<?> screen) {
        int width = panelWidth();
        int visibleCount;
        int contentWidth;
        int contentHeight;
        if (ClientConfig.BROWSER_VIEW_MODE.get() == ClientConfig.BrowserViewMode.GRID) {
            int columns = ClientConfig.BROWSER_GRID_COLUMNS.getAsInt();
            int rows = Math.min(ClientConfig.BROWSER_GRID_ROWS.getAsInt(), Math.max(1, (screen.height - 99) / GRID_CELL));
            visibleCount = columns * rows;
            contentWidth = columns * GRID_CELL;
            contentHeight = rows * GRID_CELL;
        } else {
            int rows = Math.min(Math.max(6, ClientConfig.BROWSER_GRID_ROWS.getAsInt()), Math.max(1, (screen.height - 99) / LIST_ROW));
            visibleCount = rows;
            contentWidth = width - 8;
            contentHeight = rows * LIST_ROW;
        }
        int height = 95 + contentHeight;
        ClientConfig.BrowserDockSide side = ClientConfig.BROWSER_DOCK_SIDE.get();
        int rawX = switch (side) {
            case LEFT -> handleX - width - 2;
            case RIGHT -> handleX + HANDLE_WIDTH + 2;
            case TOP, BOTTOM -> handleX + HANDLE_WIDTH / 2 - width / 2;
        };
        int rawY = switch (side) {
            case TOP -> handleY - height - 2;
            case BOTTOM -> handleY + HANDLE_HEIGHT + 2;
            case LEFT, RIGHT -> handleY + HANDLE_HEIGHT / 2 - height / 2;
        };
        int x = clamp(rawX, 2, Math.max(2, screen.width - width - 2));
        int y = clamp(rawY, 2, Math.max(2, screen.height - height - 2));
        int controlsY = y + 27;
        int contentX = ClientConfig.BROWSER_VIEW_MODE.get() == ClientConfig.BrowserViewMode.GRID
                ? x + (width - contentWidth) / 2 : x + 4;
        int contentY = y + 49;
        int capacityTextY = contentY + contentHeight + 3;
        int capacityBarY = capacityTextY + 11;
        int bottomY = capacityBarY + 7;
        return new PanelLayout(x, y, width, height, x + 4, y + 5, width - 8, controlsY,
                x + 2, x + 24, x + width - 22, contentX, contentY, contentWidth, contentHeight, visibleCount,
                capacityTextY, capacityBarY, x + 2, x + 24, x + width - 22, bottomY);
    }

    private static int panelWidth() {
        if (ClientConfig.BROWSER_VIEW_MODE.get() == ClientConfig.BrowserViewMode.LIST) return 170;
        return Math.max(68, ClientConfig.BROWSER_GRID_COLUMNS.getAsInt() * GRID_CELL + 8);
    }

    private static Rect2i panelBounds(AbstractContainerScreen<?> screen) {
        PanelLayout layout = layout(screen);
        return new Rect2i(layout.panelX, layout.panelY, layout.panelWidth, layout.panelHeight);
    }

    private static void ensurePosition(Screen screen) {
        if (lastScreen != screen) {
            lastScreen = screen;
            if (!initializedPosition) {
                int configuredX = ClientConfig.BROWSER_HANDLE_X.getAsInt();
                int configuredY = ClientConfig.BROWSER_HANDLE_Y.getAsInt();
                if (configuredX >= 0 && configuredY >= 0) {
                    handleX = configuredX;
                    handleY = configuredY;
                } else if (screen instanceof AbstractContainerScreen<?> container) {
                    handleX = container.getGuiLeft() + container.getXSize() + 2;
                    handleY = container.getGuiTop() + container.getYSize() / 2 - HANDLE_HEIGHT / 2;
                }
                initializedPosition = true;
            }
            handleX = clamp(handleX, 0, screen.width - HANDLE_WIDTH);
            handleY = clamp(handleY, 0, screen.height - HANDLE_HEIGHT);
        }
    }

    private static boolean supports(Screen screen) { return screen instanceof AbstractContainerScreen<?>; }
    private static boolean inside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }
    private static int clamp(int value, int minimum, int maximum) { return Math.max(minimum, Math.min(maximum, value)); }

    private record PanelLayout(
            int panelX, int panelY, int panelWidth, int panelHeight,
            int searchX, int searchY, int searchWidth, int controlsY,
            int categoryX, int sortX, int transferX,
            int contentX, int contentY, int contentWidth, int contentHeight, int visibleEntryCount,
            int capacityTextY, int capacityBarY, int directionX, int manageX, int settingsX, int bottomButtonY
    ) {}

    private static final class SearchIdentity {
        private final ItemStack stack;
        private final int hash;
        private SearchIdentity(ItemStack stack) {
            this.stack = stack.copyWithCount(1);
            this.hash = ItemStack.hashItemAndComponents(stack);
        }
        @Override public int hashCode() { return hash; }
        @Override public boolean equals(Object other) {
            return other instanceof SearchIdentity identity && ItemStack.isSameItemSameComponents(stack, identity.stack);
        }
    }
}
