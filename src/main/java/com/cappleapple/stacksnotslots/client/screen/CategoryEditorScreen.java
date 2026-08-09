package com.cappleapple.stacksnotslots.client.screen;

import com.cappleapple.stacksnotslots.StacksNotSlots;
import com.cappleapple.stacksnotslots.category.CategoryDefinition;
import com.cappleapple.stacksnotslots.category.CategoryRule;
import com.cappleapple.stacksnotslots.category.PlayerCategoryData;
import com.cappleapple.stacksnotslots.category.SortMode;
import com.cappleapple.stacksnotslots.category.StackTags;
import com.cappleapple.stacksnotslots.client.ClientTooltipSearchIndex;
import com.cappleapple.stacksnotslots.client.ItemSearchExpression;
import com.cappleapple.stacksnotslots.data.ModAttachments;
import com.cappleapple.stacksnotslots.network.CategoryEditPayload;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

public final class CategoryEditorScreen extends Screen {
    private static final int PANEL_WIDTH = 360;
    private static final int COLUMN_WIDTH = 176;
    private static final int LIST_TOP = 136;
    private static final int ROW_HEIGHT = 20;
    private final Screen parent;
    private final PlayerCategoryData categoryData;
    private final java.util.UUID playerId;
    private final CategoryDefinition original;
    private final ArrayList<CategoryRule> includes = new ArrayList<>();
    private final ArrayList<CategoryRule> excludes = new ArrayList<>();
    private final ArrayList<Suggestion> suggestions = new ArrayList<>();
    private EditBox name;
    private EditBox icon;
    private EditBox pickupLimit;
    private EditBox ruleSearch;
    private Button modeButton;
    private Button sortButton;
    private EditMode editMode = EditMode.INCLUDE;
    private SortMode sortMode;
    private boolean enabled;
    private int suggestionScroll;
    private int ruleScroll;

    public CategoryEditorScreen(Screen parent, net.minecraft.world.entity.player.Player player, CategoryDefinition original) {
        super(Component.translatable(original == null ? "gui.stacksnotslots.add_tab" : "gui.stacksnotslots.edit_tab"));
        this.parent = parent;
        this.categoryData = player.getData(ModAttachments.PLAYER_DATA).categories();
        this.playerId = player.getUUID();
        this.original = original;
        if (original != null) { includes.addAll(original.includes()); excludes.addAll(original.excludes()); }
        sortMode = original == null ? SortMode.NAME_ASCENDING : original.sortMode();
        enabled = original == null || original.enabled();
    }

    @Override
    protected void init() {
        int left = width / 2 - PANEL_WIDTH / 2;
        name = field(left, 38, COLUMN_WIDTH, original == null ? "New Tab" : original.displayName(),
                "gui.stacksnotslots.name", "tooltip.stacksnotslots.name");
        String iconValue = original == null ? "" : CategoryIcons.formatIcon(original.icon());
        icon = field(left + 184, 38, COLUMN_WIDTH, iconValue,
                "gui.stacksnotslots.icon", "tooltip.stacksnotslots.icon");
        pickupLimit = field(left, 73, COLUMN_WIDTH, Long.toString(original == null ? -1 : original.pickupLimit()),
                "gui.stacksnotslots.pickup_limit", "tooltip.stacksnotslots.pickup_limit");
        sortButton = addRenderableWidget(Button.builder(Component.empty(), ignored -> cycleSort())
                .tooltip(Tooltip.create(Component.translatable("tooltip.stacksnotslots.sort")))
                .bounds(left + 184, 73, COLUMN_WIDTH, 20).build());
        ruleSearch = field(left, 110, 260, "", "gui.stacksnotslots.find_item_or_tag", "tooltip.stacksnotslots.rule_search");
        ruleSearch.setResponder(this::updateSuggestions);
        modeButton = addRenderableWidget(Button.builder(Component.empty(), ignored -> cycleMode())
                .tooltip(Tooltip.create(Component.translatable("tooltip.stacksnotslots.edit_mode")))
                .bounds(left + 268, 110, 92, 20).build());

        addRenderableWidget(Button.builder(Component.translatable("gui.stacksnotslots.clear_visible_rules"), ignored -> {
                    activeRules().clear();
                    ruleScroll = 0;
                }).tooltip(Tooltip.create(Component.translatable("tooltip.stacksnotslots.clear_visible_rules")))
                .bounds(left, height - 55, 116, 20).build());
        addRenderableWidget(Button.builder(Component.translatable(enabled ? "gui.stacksnotslots.enabled" : "gui.stacksnotslots.disabled"), button -> {
            enabled = !enabled;
            button.setMessage(Component.translatable(enabled ? "gui.stacksnotslots.enabled" : "gui.stacksnotslots.disabled"));
        }).tooltip(Tooltip.create(Component.translatable("tooltip.stacksnotslots.enabled")))
                .bounds(left + 120, height - 55, 116, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), ignored -> onClose())
                .bounds(left + 240, height - 55, 120, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), ignored -> save())
                .bounds(left, height - 30, PANEL_WIDTH, 20).build());
        updateButtons();
    }

    private EditBox field(int x, int y, int fieldWidth, String value, String hintKey, String tooltipKey) {
        EditBox box = new EditBox(font, x, y, fieldWidth, 20, Component.translatable(hintKey));
        box.setValue(value);
        box.setHint(Component.translatable(hintKey));
        box.setTooltip(Tooltip.create(Component.translatable(tooltipKey)));
        box.setMaxLength(128);
        addRenderableWidget(box);
        return box;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int left = width / 2 - PANEL_WIDTH / 2;
        graphics.drawCenteredString(font, title, width / 2, 15, 0xFFFFFF);
        graphics.drawString(font, Component.translatable("gui.stacksnotslots.search_results"), left, 98, 0xA0A0A0, false);
        graphics.drawString(font, Component.translatable(editMode == EditMode.EXCLUDE
                        ? "gui.stacksnotslots.exclude_rules" : "gui.stacksnotslots.include_rules"),
                left + 184, 98, 0xA0A0A0, false);

        int rows = visibleRows();
        for (int row = 0; row < rows; row++) {
            int index = suggestionScroll + row;
            if (index >= suggestions.size()) break;
            Suggestion suggestion = suggestions.get(index);
            int y = LIST_TOP + row * ROW_HEIGHT;
            boolean hovered = inside(mouseX, mouseY, left, y, COLUMN_WIDTH, ROW_HEIGHT - 1);
            graphics.fill(left, y, left + COLUMN_WIDTH, y + ROW_HEIGHT - 1, hovered ? 0xA04F72A5 : 0xA0202020);
            graphics.renderItem(CategoryIcons.displayStack(suggestion.rule()), left + 2, y + 1);
            graphics.drawString(font, font.plainSubstrByWidth(suggestion.label(), COLUMN_WIDTH - 25), left + 22, y + 6, 0xFFFFFF, false);
        }

        List<CategoryRule> rules = activeRules();
        ruleScroll = Math.min(ruleScroll, Math.max(0, rules.size() - rows));
        for (int row = 0; row < rows; row++) {
            int index = ruleScroll + row;
            if (index >= rules.size()) break;
            CategoryRule rule = rules.get(index);
            int x = left + 184;
            int y = LIST_TOP + row * ROW_HEIGHT;
            boolean hovered = inside(mouseX, mouseY, x, y, COLUMN_WIDTH, ROW_HEIGHT - 1);
            graphics.fill(x, y, x + COLUMN_WIDTH, y + ROW_HEIGHT - 1, hovered ? 0xA04F72A5 : 0xA0202020);
            graphics.renderItem(CategoryIcons.displayStack(rule), x + 2, y + 1);
            String label = switch (rule.type()) {
                case ITEM -> rule.target().toString();
                case TAG -> "#" + rule.target();
                case MOD_ID -> "@" + rule.target().getNamespace();
                case REGEX -> "/" + rule.expression();
            };
            graphics.drawString(font, font.plainSubstrByWidth(label, COLUMN_WIDTH - 39), x + 22, y + 6, 0xFFFFFF, false);
            graphics.drawString(font, "×", x + COLUMN_WIDTH - 12, y + 6, 0xFF7777, false);
        }
        graphics.drawString(font, Component.translatable("gui.stacksnotslots.rule_counts", includes.size(), excludes.size()),
                left, height - 67, 0xD0D0D0, false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && mouseY >= LIST_TOP && mouseY < LIST_TOP + visibleRows() * ROW_HEIGHT) {
            int left = width / 2 - PANEL_WIDTH / 2;
            if (mouseX >= left && mouseX < left + COLUMN_WIDTH) {
                int index = suggestionScroll + ((int)mouseY - LIST_TOP) / ROW_HEIGHT;
                if (index < suggestions.size()) {
                    applySuggestion(suggestions.get(index));
                    return true;
                }
            }
            if (mouseX >= left + 184 && mouseX < left + 184 + COLUMN_WIDTH) {
                int index = ruleScroll + ((int)mouseY - LIST_TOP) / ROW_HEIGHT;
                List<CategoryRule> rules = activeRules();
                if (index < rules.size()) {
                    rules.remove(index);
                    ruleScroll = Math.min(ruleScroll, Math.max(0, rules.size() - visibleRows()));
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int left = width / 2 - PANEL_WIDTH / 2;
        if (mouseY >= LIST_TOP && mouseY < LIST_TOP + visibleRows() * ROW_HEIGHT) {
            if (mouseX >= left && mouseX < left + COLUMN_WIDTH) {
                suggestionScroll = scroll(suggestionScroll, suggestions.size(), scrollY);
                return true;
            }
            if (mouseX >= left + 184 && mouseX < left + 184 + COLUMN_WIDTH) {
                ruleScroll = scroll(ruleScroll, activeRules().size(), scrollY);
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override public void onClose() { minecraft.setScreen(parent); }

    private int visibleRows() {
        return Math.max(1, (height - 70 - LIST_TOP) / ROW_HEIGHT);
    }

    private int scroll(int current, int size, double delta) {
        int maximum = Math.max(0, size - visibleRows());
        return Math.max(0, Math.min(maximum, current - (int)Math.signum(delta)));
    }

    private void updateSuggestions(String raw) {
        suggestions.clear();
        suggestionScroll = 0;
        if (raw.isBlank()) {
            ruleSearch.setTextColor(0xE0E0E0);
            return;
        }
        ItemSearchExpression search = ItemSearchExpression.parse(raw);
        ruleSearch.setTextColor(search.valid() ? 0xE0E0E0 : 0xFF5555);
        if (!search.valid()) return;
        if (search.mode() == ItemSearchExpression.Mode.REGEX) {
            CategoryRule rule = CategoryRule.regex(raw);
            suggestions.add(new Suggestion(rule.encoded(), rule));
        } else if (search.mode() == ItemSearchExpression.Mode.MOD) {
            Set<String> seen = new HashSet<>();
            for (Item item : BuiltInRegistries.ITEM) {
                String namespace = BuiltInRegistries.ITEM.getKey(item).getNamespace();
                if (seen.add(namespace) && namespace.toLowerCase(Locale.ROOT).contains(search.term())) {
                    suggestions.add(new Suggestion("@" + namespace,
                            new CategoryRule(CategoryRule.Type.MOD_ID, ResourceLocation.fromNamespaceAndPath(namespace, "mod"))));
                }
            }
        } else if (search.mode() == ItemSearchExpression.Mode.TAG) {
            Set<ResourceLocation> seen = new HashSet<>();
            for (Item item : BuiltInRegistries.ITEM) {
                StackTags.locations(item.getDefaultInstance()).forEach(tag -> {
                    if (seen.add(tag) && tag.toString().toLowerCase(Locale.ROOT).contains(search.term())) {
                        suggestions.add(new Suggestion("#" + tag, new CategoryRule(CategoryRule.Type.TAG, tag)));
                    }
                });
            }
        } else {
            for (Item item : BuiltInRegistries.ITEM) {
                ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
                ItemStack stack = item.getDefaultInstance();
                if (search.matches(stack, () -> ClientTooltipSearchIndex.text(stack))) {
                    suggestions.add(new Suggestion(stack.getHoverName().getString() + "  (" + id + ")",
                            new CategoryRule(CategoryRule.Type.ITEM, id)));
                }
            }
        }
    }

    private void applySuggestion(Suggestion suggestion) {
        switch (editMode) {
            case INCLUDE -> toggle(includes, suggestion.rule());
            case EXCLUDE -> toggle(excludes, suggestion.rule());
        }
    }

    private static void toggle(List<CategoryRule> rules, CategoryRule rule) {
        if (!rules.remove(rule)) rules.add(rule);
    }

    private List<CategoryRule> activeRules() {
        return editMode == EditMode.EXCLUDE ? excludes : includes;
    }

    private void cycleMode() {
        editMode = EditMode.values()[(editMode.ordinal() + 1) % EditMode.values().length];
        ruleScroll = 0;
        updateButtons();
    }

    private void cycleSort() {
        sortMode = SortMode.values()[(sortMode.ordinal() + 1) % SortMode.values().length];
        updateButtons();
    }

    private void updateButtons() {
        modeButton.setMessage(Component.translatable("gui.stacksnotslots.edit_mode",
                Component.translatable("edit_mode.stacksnotslots." + editMode.name().toLowerCase(Locale.ROOT))));
        sortButton.setMessage(Component.translatable("gui.stacksnotslots.sort",
                Component.translatable("sort.stacksnotslots." + sortMode.name().toLowerCase(Locale.ROOT))));
    }

    private void save() {
        String iconInput = icon.getValue().trim();
        ResourceLocation iconId = CategoryIcons.parseIcon(iconInput);
        if (name.getValue().isBlank() || iconId == null
                || !iconInput.isBlank() && !iconInput.startsWith("#") && BuiltInRegistries.ITEM.getOptional(iconId).isEmpty()) return;
        long limit;
        try { limit = Long.parseLong(pickupLimit.getValue().trim()); }
        catch (NumberFormatException ignored) { return; }
        if (limit < -1 || limit > Integer.MAX_VALUE) return;
        ResourceLocation id = original == null
                ? StacksNotSlots.id("player/" + playerId.toString().replace("-", "") + "/" + Long.toUnsignedString(System.nanoTime(), 36))
                : original.id();
        int order = original == null ? categoryData.categories().size() * 10 : original.order();
        CategoryDefinition category = new CategoryDefinition(id, name.getValue().trim(), iconId, order, includes, excludes, limit, sortMode, enabled,
                original != null && original.allItems());
        PacketDistributor.sendToServer(new CategoryEditPayload(CategoryEditPayload.Operation.UPSERT, PlayerCategoryData.saveCategory(category)));
        onClose();
    }

    private static boolean inside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private enum EditMode { INCLUDE, EXCLUDE }
    private record Suggestion(String label, CategoryRule rule) {}
}
