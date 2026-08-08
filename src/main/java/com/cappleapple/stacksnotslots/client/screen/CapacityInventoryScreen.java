package com.cappleapple.stacksnotslots.client.screen;

import com.cappleapple.stacksnotslots.api.LogicalInventoryEntry;
import com.cappleapple.stacksnotslots.category.CategoryDefinition;
import com.cappleapple.stacksnotslots.category.CategoryMatcher;
import com.cappleapple.stacksnotslots.category.SortMode;
import com.cappleapple.stacksnotslots.data.ModAttachments;
import com.cappleapple.stacksnotslots.data.PlayerInventoryData;
import com.cappleapple.stacksnotslots.network.InventoryActionPayload;
import com.cappleapple.stacksnotslots.network.HotbarBindPayload;
import com.cappleapple.stacksnotslots.network.InventoryViewPreferencesPayload;
import com.cappleapple.stacksnotslots.hotbar.BindingType;
import com.cappleapple.stacksnotslots.config.ClientConfig;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.neoforged.neoforge.network.PacketDistributor;

public final class CapacityInventoryScreen extends InventoryScreen {
    private static final int VANILLA_WIDTH = 176;
    private static final int PANEL_X = 182;
    private static final int PANEL_WIDTH = 244;
    private static final int ROW_Y = 34;
    private static final int ROW_HEIGHT = 18;
    private static final int VISIBLE_ROWS = 6;
    private final Player player;
    private EditBox search;
    private Button categoryButton;
    private Button sortButton;
    private List<LogicalInventoryEntry> visibleEntries = List.of();
    private int categoryIndex;
    private int scrollOffset;
    private long cachedRevision = -1;
    private long tooltipIndexRevision = -1;
    private String cachedQuery = "";
    private LogicalInventoryEntry hoveredEntry;
    private SortMode sortMode = SortMode.NAME_ASCENDING;
    private final Map<SearchIdentity, String> tooltipIndex = new HashMap<>();

    public CapacityInventoryScreen(Player player) {
        super(player);
        this.player = player;
        this.imageWidth = PANEL_X + PANEL_WIDTH;
    }

    @Override
    protected void init() {
        super.init();
        sortMode = data().inventorySortPreference();
        ResourceLocation selectedCategory = data().selectedCategoryPreference();
        List<CategoryDefinition> categories = visibleCategories();
        if (selectedCategory != null) {
            int selectedIndex = java.util.stream.IntStream.range(0, categories.size())
                    .filter(index -> categories.get(index).id().equals(selectedCategory)).findFirst().orElse(0);
            categoryIndex = selectedIndex;
        }
        search = new EditBox(font, leftPos + PANEL_X + 4, topPos + 5, 132, 18, Component.translatable("gui.stacksnotslots.search"));
        search.setHint(Component.translatable("gui.stacksnotslots.search_hint"));
        search.setMaxLength(96);
        search.setResponder(ignored -> { scrollOffset = 0; rebuildEntries(); });
        addRenderableWidget(search);
        addRenderableWidget(Button.builder(Component.literal("<"), ignored -> changeCategory(-1)).bounds(leftPos + PANEL_X + 140, topPos + 5, 18, 18).build());
        categoryButton = addRenderableWidget(Button.builder(Component.empty(), ignored -> changeCategory(1)).bounds(leftPos + PANEL_X + 160, topPos + 5, 78, 18).build());
        sortButton = addRenderableWidget(Button.builder(Component.empty(), ignored -> cycleSort()).bounds(leftPos + PANEL_X + 4, topPos + 144, 112, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.stacksnotslots.manage_tabs"), ignored -> minecraft.setScreen(new CategoryManagerScreen(this, player)))
                .bounds(leftPos + PANEL_X + 120, topPos + 144, 118, 18).build());
        updateButtons();
        rebuildEntries();
    }

    public void focusSearch() {
        if (search != null) { setFocused(search); search.setFocused(true); }
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int previousWidth = imageWidth;
        imageWidth = VANILLA_WIDTH;
        int vanillaLeft = leftPos;
        super.renderBg(graphics, partialTick, mouseX, mouseY);
        imageWidth = previousWidth;
        graphics.fill(vanillaLeft + PANEL_X, topPos, vanillaLeft + PANEL_X + PANEL_WIDTH, topPos + imageHeight, 0xE0C6C6C6);
        graphics.fill(vanillaLeft + PANEL_X + 2, topPos + 2, vanillaLeft + PANEL_X + PANEL_WIDTH - 2, topPos + imageHeight - 2, 0xE0202020);
        long used = data().inventory().usedCapacity();
        long capacity = data().inventory().capacity();
        int barWidth = PANEL_WIDTH - 12;
        int filled = capacity <= 0
                ? (used > 0 ? barWidth : 0)
                : used >= capacity ? barWidth : (int)(used * barWidth / capacity);
        int color = used > capacity ? 0xFFE34B4B : 0xFF54B45A;
        graphics.fill(vanillaLeft + PANEL_X + 6, topPos + 132, vanillaLeft + PANEL_X + 6 + barWidth, topPos + 138, 0xFF454545);
        graphics.fill(vanillaLeft + PANEL_X + 6, topPos + 132, vanillaLeft + PANEL_X + 6 + filled, topPos + 138, color);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        super.renderLabels(graphics, mouseX, mouseY);
        rebuildIfNeeded();
        hoveredEntry = null;
        int localMouseX = mouseX - leftPos;
        int localMouseY = mouseY - topPos;
        for (int row = 0; row < VISIBLE_ROWS; row++) {
            int index = scrollOffset + row;
            if (index >= visibleEntries.size()) break;
            LogicalInventoryEntry entry = visibleEntries.get(index);
            int y = ROW_Y + row * ROW_HEIGHT;
            if (localMouseX >= PANEL_X + 4 && localMouseX < PANEL_X + PANEL_WIDTH - 4 && localMouseY >= y && localMouseY < y + ROW_HEIGHT) {
                graphics.fill(PANEL_X + 4, y, PANEL_X + PANEL_WIDTH - 4, y + ROW_HEIGHT, 0x804F72A5);
                hoveredEntry = entry;
            }
            ItemStack stack = entry.representative();
            graphics.renderItem(stack, PANEL_X + 6, y + 1);
            graphics.drawString(font, font.plainSubstrByWidth(stack.getHoverName().getString(), 150), PANEL_X + 27, y + 5, 0xFFFFFF, false);
            graphics.drawString(font, Long.toString(entry.quantity()), PANEL_X + PANEL_WIDTH - 10 - font.width(Long.toString(entry.quantity())), y + 5, 0xE0E0E0, false);
        }
        long used = data().inventory().usedCapacity();
        long capacity = data().inventory().capacity();
        String capacityText = capacityText(used, capacity) + (used > capacity ? "  OVER CAPACITY: " + (used - capacity) : "");
        graphics.drawString(font, capacityText, PANEL_X + 6, 121, used > capacity ? 0xFF7777 : 0xFFFFFF, false);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        if (hoveredEntry != null) graphics.renderTooltip(font, hoveredEntry.representative(), mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        LogicalInventoryEntry entry = entryAt(mouseX, mouseY);
        if (entry != null && (button == 0 || button == 1)) {
            if (Screen.hasShiftDown()) {
                ResourceLocation id = BuiltInRegistries.ITEM.getKey(entry.representative().getItem());
                PacketDistributor.sendToServer(new HotbarBindPayload(player.getInventory().selected, BindingType.ITEM, id));
                return true;
            }
            InventoryActionPayload.Action action = button == 1 ? InventoryActionPayload.Action.TAKE_HALF : InventoryActionPayload.Action.TAKE_STACK;
            PacketDistributor.sendToServer(new InventoryActionPayload(action, entry.representative()));
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (minecraft.options.keyDrop.matches(keyCode, scanCode) && hoveredEntry != null && !search.isFocused()) {
            InventoryActionPayload.Action action = Screen.hasControlDown() ? InventoryActionPayload.Action.DROP_STACK : InventoryActionPayload.Action.DROP_ONE;
            PacketDistributor.sendToServer(new InventoryActionPayload(action, hoveredEntry.representative()));
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (mouseX >= leftPos + PANEL_X && mouseX < leftPos + PANEL_X + PANEL_WIDTH && mouseY >= topPos + ROW_Y && mouseY < topPos + ROW_Y + VISIBLE_ROWS * ROW_HEIGHT) {
            int max = Math.max(0, visibleEntries.size() - VISIBLE_ROWS);
            scrollOffset = Math.max(0, Math.min(max, scrollOffset - (int)Math.signum(scrollY)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private PlayerInventoryData data() { return player.getData(ModAttachments.PLAYER_DATA); }

    private void rebuildIfNeeded() {
        if (cachedRevision != data().inventory().revision() || !cachedQuery.equals(search.getValue())) rebuildEntries();
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
        scrollOffset = Math.min(scrollOffset, Math.max(0, visibleEntries.size() - VISIBLE_ROWS));
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
        Comparator<LogicalInventoryEntry> byName = Comparator.comparing(entry -> entry.representative().getHoverName().getString(), String.CASE_INSENSITIVE_ORDER);
        Comparator<LogicalInventoryEntry> byQuantity = Comparator.comparingLong(LogicalInventoryEntry::quantity);
        Comparator<LogicalInventoryEntry> byId = Comparator.comparing(entry -> BuiltInRegistries.ITEM.getKey(entry.representative().getItem()).toString());
        return switch (sortMode) {
            case NAME_ASCENDING -> byName;
            case NAME_DESCENDING -> byName.reversed();
            case QUANTITY_ASCENDING -> byQuantity.thenComparing(byName);
            case QUANTITY_DESCENDING -> byQuantity.reversed().thenComparing(byName);
            case REGISTRY_ID -> byId;
            case MOD_NAMESPACE -> Comparator.comparing((LogicalInventoryEntry entry) -> BuiltInRegistries.ITEM.getKey(entry.representative().getItem()).getNamespace()).thenComparing(byId);
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
        data().setSelectedCategoryPreference(selected == null ? null : selected.id());
        data().setInventorySortPreference(sortMode);
        PacketDistributor.sendToServer(new InventoryViewPreferencesPayload(sortMode, selected == null ? null : selected.id()));
        updateButtons();
        rebuildEntries();
    }

    private void cycleSort() {
        SortMode[] modes = SortMode.values();
        sortMode = modes[(sortMode.ordinal() + 1) % modes.length];
        data().setInventorySortPreference(sortMode);
        CategoryDefinition selected = currentCategory();
        PacketDistributor.sendToServer(new InventoryViewPreferencesPayload(sortMode, selected == null ? null : selected.id()));
        updateButtons();
        rebuildEntries();
    }

    private List<CategoryDefinition> visibleCategories() {
        return data().categories().categories().stream().filter(CategoryDefinition::enabled).toList();
    }

    private void updateButtons() {
        CategoryDefinition category = currentCategory();
        if (categoryButton != null) categoryButton.setMessage(category == null ? Component.translatable("gui.stacksnotslots.all") : Component.literal(category.displayName() + " >"));
        if (sortButton != null) sortButton.setMessage(Component.translatable("gui.stacksnotslots.sort", Component.translatable("sort.stacksnotslots." + sortMode.name().toLowerCase(Locale.ROOT))));
    }

    private LogicalInventoryEntry entryAt(double mouseX, double mouseY) {
        int x = (int)mouseX - leftPos;
        int y = (int)mouseY - topPos;
        if (x < PANEL_X + 4 || x >= PANEL_X + PANEL_WIDTH - 4 || y < ROW_Y || y >= ROW_Y + VISIBLE_ROWS * ROW_HEIGHT) return null;
        int index = scrollOffset + (y - ROW_Y) / ROW_HEIGHT;
        return index >= 0 && index < visibleEntries.size() ? visibleEntries.get(index) : null;
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
