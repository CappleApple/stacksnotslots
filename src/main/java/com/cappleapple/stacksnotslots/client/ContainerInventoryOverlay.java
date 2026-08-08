package com.cappleapple.stacksnotslots.client;

import com.cappleapple.stacksnotslots.api.LogicalInventoryEntry;
import com.cappleapple.stacksnotslots.category.CategoryDefinition;
import com.cappleapple.stacksnotslots.category.CategoryMatcher;
import com.cappleapple.stacksnotslots.category.SortMode;
import com.cappleapple.stacksnotslots.client.screen.CategoryIcons;
import com.cappleapple.stacksnotslots.client.screen.CategoryManagerScreen;
import com.cappleapple.stacksnotslots.client.screen.CapacityInventoryScreen;
import com.cappleapple.stacksnotslots.client.screen.InventoryBrowserSettingsScreen;
import com.cappleapple.stacksnotslots.config.ClientConfig;
import com.cappleapple.stacksnotslots.data.ModAttachments;
import com.cappleapple.stacksnotslots.network.BrowserTransferPayload;
import com.cappleapple.stacksnotslots.network.InventoryActionPayload;
import com.cappleapple.stacksnotslots.network.InventoryViewPreferencesPayload;
import com.cappleapple.stacksnotslots.network.StowSlotPayload;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/** A draggable, topmost logical-inventory browser shared by every container screen. */
public final class ContainerInventoryOverlay {
    private static final int HANDLE_SIZE = 20;
    private static final int CONTROL_SIZE = 20;
    private static final int GRID_CELL = 20;
    private static final int LIST_ROW = 18;
    private static final int MAX_QUERY = 96;
    private static Screen lastScreen;
    private static int handleX;
    private static int handleY;
    private static boolean initializedPosition;
    private static boolean open;
    private static boolean searchFocused;
    private static boolean dragging;
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

    public static void render(ScreenEvent.Render.Post event) {
        if (!supports(event.getScreen()) || Minecraft.getInstance().player == null) return;
        ensurePosition(event.getScreen());
        if (!ClientConfig.BROWSER_HANDLE_VISIBLE.getAsBoolean()) return;
        GuiGraphics graphics = event.getGuiGraphics();
        hoveredEntry = null;
        hoveredCategory = null;
        hoveredControl = null;
        if (open) renderPanel(event.getScreen(), graphics, event.getMouseX(), event.getMouseY());
        renderHandle(graphics, event.getMouseX(), event.getMouseY());
        if (hoveredEntry != null) graphics.renderTooltip(Minecraft.getInstance().font, hoveredEntry.representative(), event.getMouseX(), event.getMouseY());
        else if (hoveredCategory != null) graphics.renderTooltip(Minecraft.getInstance().font, Component.literal(hoveredCategory.displayName()), event.getMouseX(), event.getMouseY());
        else if (hoveredControl != null) graphics.renderTooltip(Minecraft.getInstance().font, Component.translatable(hoveredControl), event.getMouseX(), event.getMouseY());
    }

    public static void click(ScreenEvent.MouseButtonPressed.Pre event) {
        if (!supports(event.getScreen()) || Minecraft.getInstance().player == null) return;
        ensurePosition(event.getScreen());
        boolean handleVisible = ClientConfig.BROWSER_HANDLE_VISIBLE.getAsBoolean();
        double mouseX = event.getMouseX();
        double mouseY = event.getMouseY();
        if (handleVisible && event.getButton() == 0 && inside(mouseX, mouseY, handleX, handleY, HANDLE_SIZE, HANDLE_SIZE)) {
            dragging = true;
            dragOffsetX = mouseX - handleX;
            dragOffsetY = mouseY - handleY;
            pressX = mouseX;
            pressY = mouseY;
            event.setCanceled(true);
            return;
        }

        AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>)event.getScreen();
        Slot hoveredSlot = screen.getSlotUnderMouse();
        if (event.getButton() == 0 && hoveredSlot != null && hoveredSlot.container instanceof Inventory
                && hoveredSlot.getContainerSlot() >= 0 && hoveredSlot.getContainerSlot() < Inventory.INVENTORY_SIZE
                && !hoveredSlot.getItem().isEmpty()
                && (Screen.hasControlDown() || isOpen() && Screen.hasShiftDown())) {
            PacketDistributor.sendToServer(new StowSlotPayload(hoveredSlot.getContainerSlot()));
            event.setCanceled(true);
            return;
        }
        if (!handleVisible || !open) return;
        Rect2i panel = panelBounds(screen);
        if (!inside(mouseX, mouseY, panel.getX(), panel.getY(), panel.getWidth(), panel.getHeight())) {
            searchFocused = false;
            return;
        }

        PanelLayout layout = layout(screen);
        if (inside(mouseX, mouseY, layout.searchX, layout.searchY, layout.searchWidth, 18)) {
            searchFocused = true;
            event.setCanceled(true);
            return;
        }
        searchFocused = false;
        if (inside(mouseX, mouseY, layout.categoryX, layout.controlsY, CONTROL_SIZE, CONTROL_SIZE)) {
            changeCategory(1);
            event.setCanceled(true);
            return;
        }
        if (inside(mouseX, mouseY, layout.modeX, layout.controlsY, CONTROL_SIZE, CONTROL_SIZE)) {
            toggleViewMode();
            refreshDisplacedInventory(event.getScreen());
            event.setCanceled(true);
            return;
        }
        if (inside(mouseX, mouseY, layout.sortX, layout.controlsY, CONTROL_SIZE, CONTROL_SIZE)) {
            cycleSort();
            event.setCanceled(true);
            return;
        }
        if (inside(mouseX, mouseY, layout.manageX, layout.bottomButtonY, CONTROL_SIZE, CONTROL_SIZE)) {
            Minecraft.getInstance().setScreen(new CategoryManagerScreen(screen, Minecraft.getInstance().player));
            event.setCanceled(true);
            return;
        }
        if (inside(mouseX, mouseY, layout.settingsX, layout.bottomButtonY, CONTROL_SIZE, CONTROL_SIZE)) {
            Minecraft.getInstance().setScreen(new InventoryBrowserSettingsScreen(screen));
            event.setCanceled(true);
            return;
        }

        LogicalInventoryEntry entry = entryAt(screen, mouseX, mouseY);
        if (entry != null && (event.getButton() == 0 || event.getButton() == 1)) {
            ItemStack carried = Minecraft.getInstance().player.containerMenu.getCarried();
            if (!carried.isEmpty()) PacketDistributor.sendToServer(new StowSlotPayload(-1));
            else if (Screen.hasShiftDown()) PacketDistributor.sendToServer(new BrowserTransferPayload(entry.representative()));
            else PacketDistributor.sendToServer(new InventoryActionPayload(event.getButton() == 1
                    ? InventoryActionPayload.Action.TAKE_HALF : InventoryActionPayload.Action.TAKE_STACK, entry.representative()));
        }
        event.setCanceled(true);
    }

    public static void drag(ScreenEvent.MouseDragged.Pre event) {
        if (!dragging || event.getMouseButton() != 0 || !supports(event.getScreen())) return;
        handleX = clamp((int)Math.round(event.getMouseX() - dragOffsetX), 0, event.getScreen().width - HANDLE_SIZE);
        handleY = clamp((int)Math.round(event.getMouseY() - dragOffsetY), 0, event.getScreen().height - HANDLE_SIZE);
        event.setCanceled(true);
    }

    public static void release(ScreenEvent.MouseButtonReleased.Pre event) {
        if (!dragging || event.getButton() != 0) return;
        dragging = false;
        if (Math.hypot(event.getMouseX() - pressX, event.getMouseY() - pressY) < 3.0) open = !open;
        ClientConfig.BROWSER_HANDLE_X.set(handleX);
        ClientConfig.BROWSER_HANDLE_Y.set(handleY);
        event.setCanceled(true);
        refreshDisplacedInventory(event.getScreen());
    }

    public static void scroll(ScreenEvent.MouseScrolled.Pre event) {
        if (!supports(event.getScreen()) || !open || !ClientConfig.BROWSER_HANDLE_VISIBLE.getAsBoolean()) return;
        AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>)event.getScreen();
        PanelLayout layout = layout(screen);
        if (inside(event.getMouseX(), event.getMouseY(), layout.categoryX, layout.controlsY, CONTROL_SIZE, CONTROL_SIZE)) {
            changeCategory(event.getScrollDeltaY() > 0 ? -1 : 1);
            event.setCanceled(true);
            return;
        }
        if (!inside(event.getMouseX(), event.getMouseY(), layout.contentX, layout.contentY, layout.contentWidth, layout.contentHeight)) return;
        int page = ClientConfig.BROWSER_VIEW_MODE.get() == ClientConfig.BrowserViewMode.GRID
                ? ClientConfig.BROWSER_GRID_COLUMNS.getAsInt() : 1;
        int maximum = Math.max(0, entries().size() - layout.visibleEntryCount);
        scroll = clamp(scroll - (int)Math.signum(event.getScrollDeltaY()) * page, 0, maximum);
        event.setCanceled(true);
    }

    public static void keyPressed(ScreenEvent.KeyPressed.Pre event) {
        if (!supports(event.getScreen())) return;
        if (ClientKeyMappings.TOGGLE_BROWSER.isActiveAndMatches(InputConstants.getKey(event.getKeyCode(), event.getScanCode()))) {
            if (open) {
                open = false;
                ClientConfig.BROWSER_HANDLE_VISIBLE.set(false);
            } else {
                ClientConfig.BROWSER_HANDLE_VISIBLE.set(!ClientConfig.BROWSER_HANDLE_VISIBLE.getAsBoolean());
            }
            searchFocused = false;
            refreshDisplacedInventory(event.getScreen());
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
        if (isAllowedSearchCharacter(event.getCodePoint())) {
            query += event.getCodePoint();
            invalidateEntries();
            event.setCanceled(true);
        }
    }

    public static void focusSearch() {
        ClientConfig.BROWSER_HANDLE_VISIBLE.set(true);
        open = true;
        searchFocused = true;
    }

    private static boolean isAllowedSearchCharacter(char character) {
        return character >= 32 && character != 127;
    }

    private static void refreshDisplacedInventory(Screen screen) {
        if (ClientConfig.BROWSER_DISPLACES_CONTAINER.getAsBoolean()
                && screen instanceof CapacityInventoryScreen
                && Minecraft.getInstance().player != null) {
            Minecraft.getInstance().setScreen(new CapacityInventoryScreen(Minecraft.getInstance().player));
        }
    }

    public static boolean isOpen() { return open && ClientConfig.BROWSER_HANDLE_VISIBLE.getAsBoolean(); }

    public static Rect2i currentBounds(Screen screen) {
        if (!supports(screen)) return new Rect2i(0, 0, 0, 0);
        ensurePosition(screen);
        if (!ClientConfig.BROWSER_HANDLE_VISIBLE.getAsBoolean()) return new Rect2i(0, 0, 0, 0);
        if (!open) return new Rect2i(handleX, handleY, HANDLE_SIZE, HANDLE_SIZE);
        Rect2i panel = panelBounds((AbstractContainerScreen<?>)screen);
        int left = Math.min(handleX, panel.getX());
        int top = Math.min(handleY, panel.getY());
        int right = Math.max(handleX + HANDLE_SIZE, panel.getX() + panel.getWidth());
        int bottom = Math.max(handleY + HANDLE_SIZE, panel.getY() + panel.getHeight());
        return new Rect2i(left, top, right - left, bottom - top);
    }

    public static int displacementOffset(Screen screen) {
        if (!supports(screen) || !isOpen() || !ClientConfig.BROWSER_DISPLACES_CONTAINER.getAsBoolean()) return 0;
        int amount = panelWidth() / 2 + 12;
        return dockRight(screen) ? -amount : amount;
    }

    private static void renderPanel(Screen screen, GuiGraphics graphics, int mouseX, int mouseY) {
        PanelLayout layout = layout((AbstractContainerScreen<?>)screen);
        Minecraft minecraft = Minecraft.getInstance();
        var inventory = minecraft.player.getData(ModAttachments.PLAYER_DATA).inventory();
        List<LogicalInventoryEntry> entries = entries();
        scroll = Math.min(scroll, Math.max(0, entries.size() - layout.visibleEntryCount));

        graphics.fill(layout.panelX, layout.panelY, layout.panelX + layout.panelWidth, layout.panelY + layout.panelHeight, 0xF0C6C6C6);
        graphics.fill(layout.panelX + 2, layout.panelY + 2, layout.panelX + layout.panelWidth - 2, layout.panelY + layout.panelHeight - 2, 0xF0202020);
        graphics.fill(layout.searchX, layout.searchY, layout.searchX + layout.searchWidth, layout.searchY + 18,
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
        renderSquare(graphics, layout.modeX, layout.controlsY, configuredIcon(ClientConfig.VIEW_MODE_ICON.get()), mouseX, mouseY,
                "gui.stacksnotslots.view_mode_control");
        renderTextSquare(graphics, layout.sortX, layout.controlsY, shortSortName(currentSort()), mouseX, mouseY,
                "gui.stacksnotslots.sort_control");
        if (inside(mouseX, mouseY, layout.categoryX, layout.controlsY, CONTROL_SIZE, CONTROL_SIZE)) hoveredCategory = currentCategory();

        if (ClientConfig.BROWSER_VIEW_MODE.get() == ClientConfig.BrowserViewMode.GRID) {
            renderGrid(graphics, layout, entries, mouseX, mouseY);
        } else {
            renderList(graphics, layout, entries, mouseX, mouseY);
        }

        long used = inventory.usedCapacity();
        long capacity = inventory.capacity();
        graphics.drawString(minecraft.font, InventoryCountFormatter.overall(used, capacity), layout.panelX + 5,
                layout.capacityTextY, used > capacity ? 0xFF7777 : 0xFFFFFF, false);
        int barWidth = layout.panelWidth - 10;
        int filled = capacity <= 0 ? (used > 0 ? barWidth : 0) : used >= capacity ? barWidth : (int)(used * barWidth / capacity);
        graphics.fill(layout.panelX + 5, layout.capacityBarY, layout.panelX + 5 + barWidth, layout.capacityBarY + 5, 0xFF454545);
        graphics.fill(layout.panelX + 5, layout.capacityBarY, layout.panelX + 5 + filled, layout.capacityBarY + 5,
                used > capacity ? 0xFFE34B4B : 0xFF54B45A);
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
        boolean hovered = inside(mouseX, mouseY, handleX, handleY, HANDLE_SIZE, HANDLE_SIZE);
        graphics.fill(handleX, handleY, handleX + HANDLE_SIZE, handleY + HANDLE_SIZE, hovered ? 0xFFF0F0F0 : 0xFFC6C6C6);
        graphics.fill(handleX + 2, handleY + 2, handleX + HANDLE_SIZE - 2, handleY + HANDLE_SIZE - 2, hovered ? 0xFF4F72A5 : 0xFF555555);
        String arrow = dockRight(lastScreen) ? (open ? "<" : ">") : (open ? ">" : "<");
        graphics.drawCenteredString(Minecraft.getInstance().font, arrow, handleX + HANDLE_SIZE / 2, handleY + 6, 0xFFFFFF);
        if (hovered) hoveredControl = "gui.stacksnotslots.inventory_browser_drag";
    }

    private static void renderSquare(GuiGraphics graphics, int x, int y, ItemStack icon, int mouseX, int mouseY, String tooltip) {
        boolean hovered = inside(mouseX, mouseY, x, y, CONTROL_SIZE, CONTROL_SIZE);
        graphics.fill(x, y, x + CONTROL_SIZE, y + CONTROL_SIZE, hovered ? 0xFF7A7A7A : 0xFF555555);
        graphics.renderItem(icon, x + 2, y + 2);
        if (hovered && hoveredControl == null) hoveredControl = tooltip;
    }

    private static void renderTextSquare(GuiGraphics graphics, int x, int y, String text, int mouseX, int mouseY, String tooltip) {
        boolean hovered = inside(mouseX, mouseY, x, y, CONTROL_SIZE, CONTROL_SIZE);
        graphics.fill(x, y, x + CONTROL_SIZE, y + CONTROL_SIZE, hovered ? 0xFF7A7A7A : 0xFF555555);
        graphics.drawCenteredString(Minecraft.getInstance().font, text, x + CONTROL_SIZE / 2, y + 6, 0xFFFFFF);
        if (hovered) hoveredControl = tooltip;
    }

    private static LogicalInventoryEntry entryAt(AbstractContainerScreen<?> screen, double mouseX, double mouseY) {
        PanelLayout layout = layout(screen);
        if (!inside(mouseX, mouseY, layout.contentX, layout.contentY, layout.contentWidth, layout.contentHeight)) return null;
        int offset;
        if (ClientConfig.BROWSER_VIEW_MODE.get() == ClientConfig.BrowserViewMode.GRID) {
            int column = ((int)mouseX - layout.contentX) / GRID_CELL;
            int row = ((int)mouseY - layout.contentY) / GRID_CELL;
            if (column >= ClientConfig.BROWSER_GRID_COLUMNS.getAsInt() || row >= ClientConfig.BROWSER_GRID_ROWS.getAsInt()) return null;
            offset = row * ClientConfig.BROWSER_GRID_COLUMNS.getAsInt() + column;
        } else {
            offset = ((int)mouseY - layout.contentY) / LIST_ROW;
        }
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
        for (LogicalInventoryEntry entry : data.inventory().entries()) {
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

    private static void toggleViewMode() {
        ClientConfig.BrowserViewMode current = ClientConfig.BROWSER_VIEW_MODE.get();
        ClientConfig.BROWSER_VIEW_MODE.set(current == ClientConfig.BrowserViewMode.GRID
                ? ClientConfig.BrowserViewMode.LIST : ClientConfig.BrowserViewMode.GRID);
        scroll = 0;
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

    private static PanelLayout layout(AbstractContainerScreen<?> screen) {
        int width = panelWidth();
        int visibleCount;
        int contentWidth;
        int contentHeight;
        if (ClientConfig.BROWSER_VIEW_MODE.get() == ClientConfig.BrowserViewMode.GRID) {
            int columns = ClientConfig.BROWSER_GRID_COLUMNS.getAsInt();
            int rows = Math.min(ClientConfig.BROWSER_GRID_ROWS.getAsInt(), Math.max(1, (screen.height - 103) / GRID_CELL));
            visibleCount = columns * rows;
            contentWidth = columns * GRID_CELL;
            contentHeight = rows * GRID_CELL;
        } else {
            int rows = Math.min(Math.max(6, ClientConfig.BROWSER_GRID_ROWS.getAsInt()), Math.max(1, (screen.height - 103) / LIST_ROW));
            visibleCount = rows;
            contentWidth = width - 8;
            contentHeight = rows * LIST_ROW;
        }
        int height = 53 + contentHeight + 46;
        int rawX = dockRight(screen) ? handleX + HANDLE_SIZE + 2 : handleX - width - 2;
        int x = clamp(rawX, 2, Math.max(2, screen.width - width - 2));
        int y = clamp(handleY + HANDLE_SIZE / 2 - height / 2, 2, Math.max(2, screen.height - height - 2));
        int controlsY = y + 27;
        int categoryX = x + width - 64;
        int modeX = x + width - 42;
        int sortX = x + width - 20;
        int contentX = ClientConfig.BROWSER_VIEW_MODE.get() == ClientConfig.BrowserViewMode.GRID
                ? x + (width - contentWidth) / 2 : x + 4;
        int contentY = y + 51;
        int capacityTextY = contentY + contentHeight + 3;
        int capacityBarY = capacityTextY + 11;
        int bottomY = capacityBarY + 7;
        return new PanelLayout(x, y, width, height, x + 4, y + 5, width - 8, controlsY,
                categoryX, modeX, sortX, contentX, contentY, contentWidth, contentHeight, visibleCount,
                capacityTextY, capacityBarY, x + 4, x + 26, bottomY);
    }

    private static int panelWidth() {
        if (ClientConfig.BROWSER_VIEW_MODE.get() == ClientConfig.BrowserViewMode.LIST) return 170;
        return Math.max(96, ClientConfig.BROWSER_GRID_COLUMNS.getAsInt() * GRID_CELL + 8);
    }

    private static Rect2i panelBounds(AbstractContainerScreen<?> screen) {
        PanelLayout layout = layout(screen);
        return new Rect2i(layout.panelX, layout.panelY, layout.panelWidth, layout.panelHeight);
    }

    private static boolean dockRight(Screen screen) {
        return screen == null || handleX + HANDLE_SIZE / 2 >= screen.width / 2;
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
                    handleY = container.getGuiTop() + container.getYSize() / 2 - HANDLE_SIZE / 2;
                }
                initializedPosition = true;
            }
            handleX = clamp(handleX, 0, screen.width - HANDLE_SIZE);
            handleY = clamp(handleY, 0, screen.height - HANDLE_SIZE);
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
            int categoryX, int modeX, int sortX,
            int contentX, int contentY, int contentWidth, int contentHeight, int visibleEntryCount,
            int capacityTextY, int capacityBarY, int manageX, int settingsX, int bottomButtonY
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
