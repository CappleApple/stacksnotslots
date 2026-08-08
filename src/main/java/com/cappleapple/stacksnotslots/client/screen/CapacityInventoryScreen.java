package com.cappleapple.stacksnotslots.client.screen;

import com.cappleapple.stacksnotslots.api.LogicalInventoryEntry;
import com.cappleapple.stacksnotslots.category.CategoryDefinition;
import com.cappleapple.stacksnotslots.category.CategoryMatcher;
import com.cappleapple.stacksnotslots.category.SortMode;
import com.cappleapple.stacksnotslots.config.ClientConfig;
import com.cappleapple.stacksnotslots.client.ClientKeyMappings;
import com.cappleapple.stacksnotslots.data.ModAttachments;
import com.cappleapple.stacksnotslots.data.PlayerInventoryData;
import com.cappleapple.stacksnotslots.network.InventoryActionPayload;
import com.cappleapple.stacksnotslots.network.InventoryViewPreferencesPayload;
import com.cappleapple.stacksnotslots.network.StowSlotPayload;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.neoforged.neoforge.network.PacketDistributor;

/** Vanilla-first inventory screen with an optional dynamic inventory-browser drawer. */
public final class CapacityInventoryScreen extends InventoryScreen {
    private static final int VANILLA_WIDTH = InventoryDrawerLayout.VANILLA_WIDTH;
    private static final int DRAWER_GAP = InventoryDrawerLayout.DRAWER_GAP;
    private static final int ROW_HEIGHT = InventoryDrawerLayout.ROW_HEIGHT;
    private static final long OPEN_ANIMATION_NANOS = 140_000_000L;

    private final Player player;
    private final boolean drawerOpen;
    private final String initialQuery;
    private final boolean focusSearchOnOpen;
    private final Map<AbstractWidget, Integer> drawerWidgetTargets = new LinkedHashMap<>();
    private final Map<SearchIdentity, String> tooltipIndex = new HashMap<>();
    private EditBox search;
    private Button drawerToggle;
    private Button categoryButton;
    private Button sortButton;
    private Button compactCategorySelector;
    private List<LogicalInventoryEntry> visibleEntries = List.of();
    private int drawerWidth;
    private int drawerHeight;
    private int drawerLocalX;
    private int drawerTop;
    private int listTop;
    private int visibleRows;
    private int capacityTextY;
    private int capacityBarY;
    private int manageButtonY;
    private int drawerAnimationOffset;
    private int categoryIndex;
    private int scrollOffset;
    private long openedAtNanos;
    private long cachedRevision = -1;
    private long tooltipIndexRevision = -1;
    private String cachedQuery = "";
    private LogicalInventoryEntry hoveredEntry;
    private CategoryDefinition hoveredCategory;
    private SortMode sortMode = SortMode.NAME_ASCENDING;
    private boolean categoryMenuOpen;
    private int categoryMenuCenter;

    public CapacityInventoryScreen(Player player) {
        this(player, false, "", false);
    }

    private CapacityInventoryScreen(Player player, boolean drawerOpen, String initialQuery, boolean focusSearchOnOpen) {
        super(player);
        this.player = player;
        this.drawerOpen = drawerOpen;
        this.initialQuery = initialQuery;
        this.focusSearchOnOpen = focusSearchOnOpen;
        this.imageWidth = VANILLA_WIDTH;
    }

    @Override
    protected void init() {
        drawerWidgetTargets.clear();
        InventoryDrawerLayout layout = InventoryDrawerLayout.calculate(width, height);
        drawerWidth = layout.width();
        drawerLocalX = VANILLA_WIDTH + DRAWER_GAP;
        imageWidth = drawerOpen ? drawerLocalX + drawerWidth : VANILLA_WIDTH;
        super.init();

        sortMode = data().inventorySortPreference();
        ResourceLocation selectedCategory = data().selectedCategoryPreference();
        List<CategoryDefinition> categories = visibleCategories();
        if (selectedCategory != null) {
            categoryIndex = java.util.stream.IntStream.range(0, categories.size())
                    .filter(index -> categories.get(index).id().equals(selectedCategory)).findFirst().orElse(0);
        }
        categoryMenuCenter = categoryIndex;
        CategoryDefinition compactCategory = currentCategory();
        compactCategorySelector = addRenderableWidget(Button.builder(Component.empty(), ignored -> {
                    categoryMenuOpen = !categoryMenuOpen;
                    categoryMenuCenter = categoryIndex;
                }).tooltip(Tooltip.create(Component.literal(compactCategory == null
                        ? Component.translatable("gui.stacksnotslots.all").getString() : compactCategory.displayName())))
                .bounds(leftPos + ClientConfig.CATEGORY_SELECTOR_X.getAsInt(), topPos + ClientConfig.CATEGORY_SELECTOR_Y.getAsInt(), 20, 20).build());

        int toggleX = leftPos + VANILLA_WIDTH;
        drawerToggle = addRenderableWidget(Button.builder(Component.literal(drawerOpen ? "<" : ">"), ignored -> toggleDrawer())
                .tooltip(Tooltip.create(Component.translatable(drawerOpen
                        ? "gui.stacksnotslots.close_inventory_browser"
                        : "gui.stacksnotslots.inventory_browser")))
                .bounds(toggleX, topPos + imageHeight / 2 - 10, 18, 20).build());
        if (!drawerOpen) return;

        drawerHeight = layout.height();
        drawerTop = layout.top();
        int panelX = leftPos + drawerLocalX;
        search = addDrawerWidget(new EditBox(font, panelX + 4, drawerTop + 5, drawerWidth - 8, 18,
                Component.translatable("gui.stacksnotslots.search")));
        search.setHint(Component.translatable("gui.stacksnotslots.search_hint_compact"));
        search.setMaxLength(96);
        search.setValue(initialQuery);
        search.setResponder(ignored -> {
            scrollOffset = 0;
            rebuildEntries();
        });

        int sortWidth = Math.min(52, Math.max(38, drawerWidth / 3));
        int categoryWidth = drawerWidth - sortWidth - 10;
        categoryButton = addDrawerWidget(Button.builder(Component.empty(), ignored -> changeCategory(1))
                .bounds(panelX + 4, drawerTop + 27, categoryWidth, 18).build());
        sortButton = addDrawerWidget(Button.builder(Component.empty(), ignored -> cycleSort())
                .bounds(panelX + 6 + categoryWidth, drawerTop + 27, sortWidth, 18).build());

        manageButtonY = layout.manageButtonY();
        capacityBarY = layout.capacityBarY();
        capacityTextY = layout.capacityTextY();
        listTop = layout.listTop();
        visibleRows = layout.visibleRows();
        addDrawerWidget(Button.builder(Component.translatable("gui.stacksnotslots.manage_tabs"), ignored ->
                        minecraft.setScreen(new CategoryManagerScreen(this, player)))
                .bounds(panelX + 4, manageButtonY, drawerWidth - 8, 18).build());

        openedAtNanos = System.nanoTime();
        updateButtons();
        rebuildEntries();
        if (focusSearchOnOpen) focusSearch();
    }

    private <T extends AbstractWidget> T addDrawerWidget(T widget) {
        drawerWidgetTargets.put(widget, widget.getX() - leftPos);
        return addRenderableWidget(widget);
    }

    public void focusSearch() {
        if (!drawerOpen) {
            minecraft.setScreen(new CapacityInventoryScreen(player, true, "", true));
            return;
        }
        if (search != null) {
            setFocused(search);
            search.setFocused(true);
        }
    }

    public boolean isDrawerOpen() {
        return drawerOpen;
    }

    /** Screen-space area reserved for JEI/EMI while the drawer is expanded. */
    public Rect2i drawerBounds() {
        if (!drawerOpen) return new Rect2i(leftPos + VANILLA_WIDTH, topPos + imageHeight / 2 - 10, 18, 20);
        int x = leftPos + VANILLA_WIDTH;
        return new Rect2i(x, drawerTop, drawerLocalX + drawerWidth - VANILLA_WIDTH, drawerHeight);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int previousWidth = imageWidth;
        imageWidth = VANILLA_WIDTH;
        super.renderBg(graphics, partialTick, mouseX, mouseY);
        imageWidth = previousWidth;
        if (!drawerOpen) return;

        int panelX = leftPos + drawerLocalX + drawerAnimationOffset;
        graphics.fill(panelX, drawerTop, panelX + drawerWidth, drawerTop + drawerHeight, 0xE0C6C6C6);
        graphics.fill(panelX + 2, drawerTop + 2, panelX + drawerWidth - 2, drawerTop + drawerHeight - 2, 0xF0202020);
        int barWidth = drawerWidth - 12;
        long used = data().inventory().usedCapacity();
        long capacity = data().inventory().capacity();
        int filled = capacity <= 0
                ? (used > 0 ? barWidth : 0)
                : used >= capacity ? barWidth : (int)(used * barWidth / capacity);
        int color = used > capacity ? 0xFFE34B4B : 0xFF54B45A;
        graphics.fill(panelX + 6, capacityBarY, panelX + 6 + barWidth, capacityBarY + 6, 0xFF454545);
        graphics.fill(panelX + 6, capacityBarY, panelX + 6 + filled, capacityBarY + 6, color);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        super.renderLabels(graphics, mouseX, mouseY);
        if (!drawerOpen) return;
        rebuildIfNeeded();
        hoveredEntry = null;
        int panelX = drawerLocalX + drawerAnimationOffset;
        int localListTop = listTop - topPos;
        int localMouseX = mouseX - leftPos;
        int localMouseY = mouseY - topPos;
        for (int row = 0; row < visibleRows; row++) {
            int index = scrollOffset + row;
            if (index >= visibleEntries.size()) break;
            LogicalInventoryEntry entry = visibleEntries.get(index);
            int y = localListTop + row * ROW_HEIGHT;
            if (localMouseX >= panelX + 4 && localMouseX < panelX + drawerWidth - 4
                    && localMouseY >= y && localMouseY < y + ROW_HEIGHT) {
                graphics.fill(panelX + 4, y, panelX + drawerWidth - 4, y + ROW_HEIGHT, 0x804F72A5);
                hoveredEntry = entry;
            }
            ItemStack stack = entry.representative();
            graphics.renderItem(stack, panelX + 6, y + 1);
            String count = Long.toString(entry.quantity());
            int countWidth = font.width(count);
            int nameWidth = Math.max(12, drawerWidth - 43 - countWidth);
            graphics.drawString(font, font.plainSubstrByWidth(stack.getHoverName().getString(), nameWidth), panelX + 27, y + 5, 0xFFFFFF, false);
            graphics.drawString(font, count, panelX + drawerWidth - 8 - countWidth, y + 5, 0xE0E0E0, false);
        }
        long used = data().inventory().usedCapacity();
        long capacity = data().inventory().capacity();
        String fullText = capacityText(used, capacity) + (used > capacity ? "  +" + (used - capacity) : "");
        String compactText = used + " / " + capacity + (used > capacity ? "  +" + (used - capacity) : "");
        String displayed = font.width(fullText) <= drawerWidth - 12 ? fullText : compactText;
        graphics.drawString(font, font.plainSubstrByWidth(displayed, drawerWidth - 12), panelX + 6,
                capacityTextY - topPos, used > capacity ? 0xFF7777 : 0xFFFFFF, false);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        updateDrawerAnimation();
        super.render(graphics, mouseX, mouseY, partialTick);
        renderCompactCategorySelector(graphics, mouseX, mouseY);
        if (hoveredEntry != null) graphics.renderTooltip(font, hoveredEntry.representative(), mouseX, mouseY);
        if (hoveredCategory != null) graphics.renderTooltip(font, Component.literal(hoveredCategory.displayName()), mouseX, mouseY);
    }

    private void updateDrawerAnimation() {
        if (drawerToggle != null) drawerToggle.setX(leftPos + VANILLA_WIDTH);
        if (!drawerOpen) return;
        double progress = Math.min(1.0, (System.nanoTime() - openedAtNanos) / (double)OPEN_ANIMATION_NANOS);
        double eased = 1.0 - Math.pow(1.0 - progress, 3.0);
        drawerAnimationOffset = (int)Math.round((1.0 - eased) * (drawerWidth + 8));
        for (Map.Entry<AbstractWidget, Integer> entry : drawerWidgetTargets.entrySet()) {
            entry.getKey().setX(leftPos + entry.getValue() + drawerAnimationOffset);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && ClientKeyMappings.STOW_MODIFIER.isDown() && hoveredSlot != null
                && hoveredSlot.container instanceof Inventory
                && hoveredSlot.getContainerSlot() >= 0 && hoveredSlot.getContainerSlot() < Inventory.INVENTORY_SIZE
                && !hoveredSlot.getItem().isEmpty()) {
            PacketDistributor.sendToServer(new StowSlotPayload(hoveredSlot.getContainerSlot()));
            return true;
        }
        if (categoryMenuOpen && button == 0 && selectCategoryAt(mouseX, mouseY)) return true;
        if (drawerOpen && (button == 0 || button == 1) && isOverDrawerList(mouseX, mouseY)
                && !player.containerMenu.getCarried().isEmpty()) {
            PacketDistributor.sendToServer(new StowSlotPayload(-1));
            return true;
        }
        LogicalInventoryEntry entry = entryAt(mouseX, mouseY);
        if (entry != null && (button == 0 || button == 1)) {
            InventoryActionPayload.Action action = button == 1
                    ? InventoryActionPayload.Action.TAKE_HALF : InventoryActionPayload.Action.TAKE_STACK;
            PacketDistributor.sendToServer(new InventoryActionPayload(action, entry.representative()));
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (drawerOpen && minecraft.options.keyDrop.matches(keyCode, scanCode) && hoveredEntry != null
                && search != null && !search.isFocused()) {
            InventoryActionPayload.Action action = Screen.hasControlDown()
                    ? InventoryActionPayload.Action.DROP_STACK : InventoryActionPayload.Action.DROP_ONE;
            PacketDistributor.sendToServer(new InventoryActionPayload(action, hoveredEntry.representative()));
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (categoryMenuOpen && compactCategorySelector != null
                && mouseX >= compactCategorySelector.getX() + 20 && mouseX < compactCategorySelector.getX() + 42
                && mouseY >= compactCategorySelector.getY() - 42 && mouseY < compactCategorySelector.getY() + 58) {
            List<CategoryDefinition> categories = visibleCategories();
            if (!categories.isEmpty()) categoryMenuCenter = Math.floorMod(categoryMenuCenter - (int)Math.signum(scrollY), categories.size());
            return true;
        }
        int panelX = leftPos + drawerLocalX + drawerAnimationOffset;
        if (drawerOpen && mouseX >= panelX && mouseX < panelX + drawerWidth
                && mouseY >= listTop && mouseY < listTop + visibleRows * ROW_HEIGHT) {
            int max = Math.max(0, visibleEntries.size() - visibleRows);
            scrollOffset = Math.max(0, Math.min(max, scrollOffset - (int)Math.signum(scrollY)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private void toggleDrawer() {
        String query = search == null ? "" : search.getValue();
        minecraft.setScreen(new CapacityInventoryScreen(player, !drawerOpen, query, false));
    }

    private PlayerInventoryData data() {
        return player.getData(ModAttachments.PLAYER_DATA);
    }

    private void rebuildIfNeeded() {
        if (search != null && (cachedRevision != data().inventory().revision() || !cachedQuery.equals(search.getValue()))) {
            rebuildEntries();
        }
    }

    private void rebuildEntries() {
        if (search == null) return;
        cachedRevision = data().inventory().revision();
        if (tooltipIndexRevision != cachedRevision) {
            tooltipIndex.clear();
            tooltipIndexRevision = cachedRevision;
        }
        cachedQuery = search.getValue();
        CategoryDefinition category = currentCategory();
        ArrayList<LogicalInventoryEntry> entries = new ArrayList<>();
        for (LogicalInventoryEntry entry : data().inventory().entries()) {
            if (category != null && !CategoryMatcher.matches(category, entry.representative())) continue;
            if (!matchesSearch(entry.representative(), cachedQuery)) continue;
            entries.add(entry);
        }
        entries.sort(comparator());
        visibleEntries = List.copyOf(entries);
        scrollOffset = Math.min(scrollOffset, Math.max(0, visibleEntries.size() - visibleRows));
    }

    private boolean matchesSearch(ItemStack stack, String rawQuery) {
        String query = rawQuery.trim().toLowerCase(Locale.ROOT);
        if (query.isEmpty()) return true;
        if (query.startsWith("#")) return stack.getTags().anyMatch(tag -> tag.location().toString().contains(query.substring(1)));
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (stack.getHoverName().getString().toLowerCase(Locale.ROOT).contains(query)
                || id.toString().contains(query) || id.getNamespace().contains(query)) return true;
        if (!ClientConfig.TOOLTIP_INDEXING.getAsBoolean()) return false;
        String tooltipText = tooltipIndex.computeIfAbsent(new SearchIdentity(stack), identity ->
                identity.stack.getTooltipLines(Item.TooltipContext.of(player.level()), player, TooltipFlag.Default.NORMAL).stream()
                        .skip(1)
                        .map(Component::getString)
                        .map(line -> line.toLowerCase(Locale.ROOT))
                        .reduce("", (left, right) -> left + '\n' + right));
        return tooltipText.contains(query);
    }

    private Comparator<LogicalInventoryEntry> comparator() {
        Comparator<LogicalInventoryEntry> byName = Comparator.comparing(
                entry -> entry.representative().getHoverName().getString(), String.CASE_INSENSITIVE_ORDER);
        Comparator<LogicalInventoryEntry> byQuantity = Comparator.comparingLong(LogicalInventoryEntry::quantity);
        Comparator<LogicalInventoryEntry> byId = Comparator.comparing(
                entry -> BuiltInRegistries.ITEM.getKey(entry.representative().getItem()).toString());
        return switch (sortMode) {
            case NAME_ASCENDING -> byName;
            case NAME_DESCENDING -> byName.reversed();
            case QUANTITY_ASCENDING -> byQuantity.thenComparing(byName);
            case QUANTITY_DESCENDING -> byQuantity.reversed().thenComparing(byName);
            case REGISTRY_ID -> byId;
            case MOD_NAMESPACE -> Comparator.comparing((LogicalInventoryEntry entry) ->
                    BuiltInRegistries.ITEM.getKey(entry.representative().getItem()).getNamespace()).thenComparing(byId);
        };
    }

    private CategoryDefinition currentCategory() {
        List<CategoryDefinition> categories = visibleCategories();
        if (categories.isEmpty()) return null;
        categoryIndex = Math.floorMod(categoryIndex, categories.size());
        return categories.get(categoryIndex);
    }

    private void changeCategory(int direction) {
        List<CategoryDefinition> categories = visibleCategories();
        if (!categories.isEmpty()) categoryIndex = Math.floorMod(categoryIndex + direction, categories.size());
        scrollOffset = 0;
        CategoryDefinition selected = currentCategory();
        if (selected != null) sortMode = selected.sortMode();
        applyViewPreferences(selected);
        updateButtons();
        rebuildEntries();
    }

    private void cycleSort() {
        SortMode[] modes = SortMode.values();
        sortMode = modes[(sortMode.ordinal() + 1) % modes.length];
        applyViewPreferences(currentCategory());
        updateButtons();
        rebuildEntries();
    }

    private void applyViewPreferences(CategoryDefinition selected) {
        ResourceLocation selectedId = selected == null ? null : selected.id();
        data().setSelectedCategoryPreference(selectedId);
        data().setInventorySortPreference(sortMode);
        PacketDistributor.sendToServer(new InventoryViewPreferencesPayload(sortMode, selectedId));
    }

    private List<CategoryDefinition> visibleCategories() {
        return data().categories().categories().stream().filter(CategoryDefinition::enabled).toList();
    }

    private void updateButtons() {
        CategoryDefinition category = currentCategory();
        if (compactCategorySelector != null) {
            compactCategorySelector.setTooltip(Tooltip.create(Component.literal(category == null
                    ? Component.translatable("gui.stacksnotslots.all").getString() : category.displayName())));
        }
        if (categoryButton != null) {
            String name = category == null ? Component.translatable("gui.stacksnotslots.all").getString() : category.displayName();
            categoryButton.setMessage(Component.literal(font.plainSubstrByWidth(name, categoryButton.getWidth() - 12) + " >"));
        }
        if (sortButton != null) sortButton.setMessage(Component.literal(shortSortName(sortMode)));
    }

    private LogicalInventoryEntry entryAt(double mouseX, double mouseY) {
        if (!drawerOpen) return null;
        int panelX = leftPos + drawerLocalX + drawerAnimationOffset;
        if (mouseX < panelX + 4 || mouseX >= panelX + drawerWidth - 4
                || mouseY < listTop || mouseY >= listTop + visibleRows * ROW_HEIGHT) return null;
        int index = scrollOffset + ((int)mouseY - listTop) / ROW_HEIGHT;
        return index >= 0 && index < visibleEntries.size() ? visibleEntries.get(index) : null;
    }

    private boolean isOverDrawerList(double mouseX, double mouseY) {
        if (!drawerOpen) return false;
        int panelX = leftPos + drawerLocalX + drawerAnimationOffset;
        return mouseX >= panelX + 4 && mouseX < panelX + drawerWidth - 4
                && mouseY >= listTop && mouseY < listTop + visibleRows * ROW_HEIGHT;
    }

    private void renderCompactCategorySelector(GuiGraphics graphics, int mouseX, int mouseY) {
        hoveredCategory = null;
        if (compactCategorySelector == null) return;
        CategoryDefinition selected = currentCategory();
        graphics.renderItem(CategoryIcons.displayStack(selected), compactCategorySelector.getX() + 2, compactCategorySelector.getY() + 2);
        if (!categoryMenuOpen) return;
        List<CategoryDefinition> categories = visibleCategories();
        if (categories.isEmpty()) return;
        int x = compactCategorySelector.getX() + 22;
        for (int offset = -2; offset <= 2; offset++) {
            int index = Math.floorMod(categoryMenuCenter + offset, categories.size());
            CategoryDefinition category = categories.get(index);
            int y = compactCategorySelector.getY() + offset * 19;
            int alpha = switch (Math.abs(offset)) { case 0 -> 235; case 1 -> 170; default -> 75; };
            boolean hovered = mouseX >= x && mouseX < x + 20 && mouseY >= y && mouseY < y + 20;
            graphics.fill(x, y, x + 20, y + 20, ((hovered ? 255 : alpha) << 24) | (hovered ? 0x4F72A5 : 0x202020));
            float itemAlpha = hovered ? 1.0F : alpha / 255.0F;
            graphics.setColor(1.0F, 1.0F, 1.0F, itemAlpha);
            graphics.renderItem(CategoryIcons.displayStack(category), x + 2, y + 2);
            graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
            if (hovered) hoveredCategory = category;
        }
    }

    private boolean selectCategoryAt(double mouseX, double mouseY) {
        List<CategoryDefinition> categories = visibleCategories();
        if (compactCategorySelector == null || categories.isEmpty()) return false;
        int x = compactCategorySelector.getX() + 22;
        for (int offset = -2; offset <= 2; offset++) {
            int y = compactCategorySelector.getY() + offset * 19;
            if (mouseX < x || mouseX >= x + 20 || mouseY < y || mouseY >= y + 20) continue;
            categoryIndex = Math.floorMod(categoryMenuCenter + offset, categories.size());
            CategoryDefinition category = currentCategory();
            if (category != null) sortMode = category.sortMode();
            categoryMenuOpen = false;
            applyViewPreferences(category);
            updateButtons();
            rebuildEntries();
            return true;
        }
        return false;
    }

    private static String shortSortName(SortMode mode) {
        return switch (mode) {
            case NAME_ASCENDING -> "A-Z";
            case NAME_DESCENDING -> "Z-A";
            case QUANTITY_ASCENDING -> "1-9";
            case QUANTITY_DESCENDING -> "9-1";
            case REGISTRY_ID -> "ID";
            case MOD_NAMESPACE -> "Mod";
        };
    }

    private static String capacityText(long used, long capacity) {
        return switch (ClientConfig.CAPACITY_DISPLAY_MODE.get()) {
            case CAPACITY -> used + " / " + capacity;
            case STACK_EQUIVALENTS -> String.format(Locale.ROOT, "%.2f / %.2f stacks", used / 64.0, capacity / 64.0);
            case BOTH -> String.format(Locale.ROOT, "%d / %d  (%.2f / %.2f stacks)", used, capacity, used / 64.0, capacity / 64.0);
        };
    }

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
