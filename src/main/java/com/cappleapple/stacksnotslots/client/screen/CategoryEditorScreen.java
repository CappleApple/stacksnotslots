package com.cappleapple.stacksnotslots.client.screen;

import com.cappleapple.stacksnotslots.StacksNotSlots;
import com.cappleapple.stacksnotslots.category.CategoryDefinition;
import com.cappleapple.stacksnotslots.category.CategoryRule;
import com.cappleapple.stacksnotslots.category.PlayerCategoryData;
import com.cappleapple.stacksnotslots.category.SortMode;
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
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

public final class CategoryEditorScreen extends Screen {
    private final Screen parent;
    private final Player player;
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

    private int visibleSuggestionRows() {
        return Math.min(6, Math.max(1, (height - 80 - 136) / 20));
    }

    public CategoryEditorScreen(Screen parent, Player player, CategoryDefinition original) {
        super(Component.translatable(original == null ? "gui.stacksnotslots.add_tab" : "gui.stacksnotslots.edit_tab"));
        this.parent = parent;
        this.player = player;
        this.original = original;
        if (original != null) { includes.addAll(original.includes()); excludes.addAll(original.excludes()); }
        sortMode = original == null ? SortMode.NAME_ASCENDING : original.sortMode();
        enabled = original == null || original.enabled();
    }

    @Override
    protected void init() {
        int left = width / 2 - 150;
        name = field(left, 38, 145, original == null ? "New Tab" : original.displayName(), "gui.stacksnotslots.name");
        icon = field(left + 155, 38, 145, original == null ? "minecraft:chest" : original.icon().toString(), "gui.stacksnotslots.icon");
        pickupLimit = field(left, 73, 145, Long.toString(original == null ? -1 : original.pickupLimit()), "gui.stacksnotslots.pickup_limit");
        sortButton = addRenderableWidget(Button.builder(Component.empty(), ignored -> cycleSort()).bounds(left + 155, 73, 145, 20).build());
        ruleSearch = field(left, 110, 210, "", "gui.stacksnotslots.find_item_or_tag");
        ruleSearch.setResponder(this::updateSuggestions);
        modeButton = addRenderableWidget(Button.builder(Component.empty(), ignored -> cycleMode()).bounds(left + 215, 110, 85, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.stacksnotslots.clear_includes"), ignored -> includes.clear()).bounds(left, height - 55, 95, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.stacksnotslots.clear_excludes"), ignored -> excludes.clear()).bounds(left + 100, height - 55, 95, 20).build());
        addRenderableWidget(Button.builder(Component.translatable(enabled ? "gui.stacksnotslots.enabled" : "gui.stacksnotslots.disabled"), button -> {
            enabled = !enabled; button.setMessage(Component.translatable(enabled ? "gui.stacksnotslots.enabled" : "gui.stacksnotslots.disabled"));
        }).bounds(left + 200, height - 55, 100, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), ignored -> save()).bounds(left + 155, height - 30, 145, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), ignored -> onClose()).bounds(left, height - 30, 145, 20).build());
        updateButtons();
    }

    private EditBox field(int x, int y, int width, String value, String hintKey) {
        EditBox box = new EditBox(font, x, y, width, 20, Component.translatable(hintKey));
        box.setValue(value);
        box.setHint(Component.translatable(hintKey));
        box.setMaxLength(128);
        addRenderableWidget(box);
        return box;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        int left = width / 2 - 150;
        graphics.drawCenteredString(font, title, width / 2, 15, 0xFFFFFF);
        graphics.drawString(font, Component.translatable("gui.stacksnotslots.editor_help"), left, 98, 0xA0A0A0, false);
        for (int i = 0; i < suggestions.size() && i < visibleSuggestionRows(); i++) {
            Suggestion suggestion = suggestions.get(i);
            int y = 136 + i * 20;
            boolean hovered = mouseX >= left && mouseX < left + 300 && mouseY >= y && mouseY < y + 19;
            graphics.fill(left, y, left + 300, y + 19, hovered ? 0xA04F72A5 : 0xA0202020);
            if (!suggestion.icon().isEmpty()) graphics.renderItem(suggestion.icon(), left + 2, y + 1);
            graphics.drawString(font, suggestion.label(), left + 22, y + 6, 0xFFFFFF, false);
        }
        graphics.drawString(font, Component.translatable("gui.stacksnotslots.rule_counts", includes.size(), excludes.size()), left, Math.min(height - 67, 260), 0xD0D0D0, false);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int left = width / 2 - 150;
        if (mouseX >= left && mouseX < left + 300 && mouseY >= 136 && mouseY < 136 + visibleSuggestionRows() * 20) {
            int index = ((int)mouseY - 136) / 20;
            if (index < suggestions.size()) { applySuggestion(suggestions.get(index)); return true; }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override public void onClose() { minecraft.setScreen(parent); }

    private void updateSuggestions(String raw) {
        suggestions.clear();
        String query = raw.trim().toLowerCase(Locale.ROOT);
        if (query.isBlank()) return;
        if (query.startsWith("#")) {
            Set<ResourceLocation> seen = new HashSet<>();
            for (Item item : BuiltInRegistries.ITEM) {
                item.getDefaultInstance().getTags().forEach(tag -> {
                    if (suggestions.size() < 20 && seen.add(tag.location()) && tag.location().toString().contains(query.substring(1))) {
                        suggestions.add(new Suggestion("#" + tag.location(), new CategoryRule(CategoryRule.Type.TAG, tag.location()), ItemStack.EMPTY));
                    }
                });
            }
        } else {
            for (Item item : BuiltInRegistries.ITEM) {
                ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
                ItemStack stack = item.getDefaultInstance();
                if (!stack.isEmpty() && (id.toString().contains(query) || stack.getHoverName().getString().toLowerCase(Locale.ROOT).contains(query))) {
                    suggestions.add(new Suggestion(stack.getHoverName().getString() + "  (" + id + ")", new CategoryRule(CategoryRule.Type.ITEM, id), stack));
                    if (suggestions.size() >= 20) break;
                }
            }
        }
    }

    private void applySuggestion(Suggestion suggestion) {
        switch (editMode) {
            case INCLUDE -> toggle(includes, suggestion.rule());
            case EXCLUDE -> toggle(excludes, suggestion.rule());
            case ICON -> { if (suggestion.rule().type() == CategoryRule.Type.ITEM) icon.setValue(suggestion.rule().target().toString()); }
        }
    }

    private static void toggle(List<CategoryRule> rules, CategoryRule rule) {
        if (!rules.remove(rule)) rules.add(rule);
    }

    private void cycleMode() {
        editMode = EditMode.values()[(editMode.ordinal() + 1) % EditMode.values().length];
        updateButtons();
    }

    private void cycleSort() {
        sortMode = SortMode.values()[(sortMode.ordinal() + 1) % SortMode.values().length];
        updateButtons();
    }

    private void updateButtons() {
        modeButton.setMessage(Component.translatable("gui.stacksnotslots.edit_mode", Component.translatable("edit_mode.stacksnotslots." + editMode.name().toLowerCase(Locale.ROOT))));
        sortButton.setMessage(Component.translatable("gui.stacksnotslots.sort", Component.translatable("sort.stacksnotslots." + sortMode.name().toLowerCase(Locale.ROOT))));
    }

    private void save() {
        ResourceLocation iconId = ResourceLocation.tryParse(icon.getValue().trim());
        if (name.getValue().isBlank() || iconId == null || BuiltInRegistries.ITEM.getOptional(iconId).isEmpty()) return;
        long limit;
        try { limit = Long.parseLong(pickupLimit.getValue().trim()); }
        catch (NumberFormatException ignored) { return; }
        if (limit < -1 || limit > Integer.MAX_VALUE) return;
        ResourceLocation id = original == null
                ? StacksNotSlots.id("player/" + player.getUUID().toString().replace("-", "") + "/" + Long.toUnsignedString(System.nanoTime(), 36))
                : original.id();
        int order = original == null ? player.getData(ModAttachments.PLAYER_DATA).categories().categories().size() * 10 : original.order();
        CategoryDefinition category = new CategoryDefinition(id, name.getValue().trim(), iconId, order, includes, excludes, limit, sortMode, enabled,
                original != null && original.allItems());
        PacketDistributor.sendToServer(new CategoryEditPayload(CategoryEditPayload.Operation.UPSERT, PlayerCategoryData.saveCategory(category)));
        onClose();
    }

    private enum EditMode { INCLUDE, EXCLUDE, ICON }
    private record Suggestion(String label, CategoryRule rule, ItemStack icon) {}
}
