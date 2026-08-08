package com.cappleapple.stacksnotslots.client.screen;

import com.cappleapple.stacksnotslots.category.CategoryDefinition;
import com.cappleapple.stacksnotslots.category.PlayerCategoryData;
import com.cappleapple.stacksnotslots.data.ModAttachments;
import com.cappleapple.stacksnotslots.hotbar.BindingType;
import com.cappleapple.stacksnotslots.hotbar.HotbarBinding;
import com.cappleapple.stacksnotslots.network.CategoryEditPayload;
import com.cappleapple.stacksnotslots.network.HotbarBindPayload;
import com.cappleapple.stacksnotslots.network.PickupToHotbarPayload;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.PacketDistributor;

public final class CategoryManagerScreen extends Screen {
    private static final int ROW_HEIGHT = 23;
    private final Screen parent;
    private final Player player;
    private final Map<Button, CategoryDefinition> iconButtons = new LinkedHashMap<>();
    private int categoryScroll;
    private int bindingScroll;
    private int selectedHotbar = -1;
    private int observedFingerprint;
    private int listBottom;
    private int pickerY;

    public CategoryManagerScreen(Screen parent, Player player) {
        super(Component.translatable("gui.stacksnotslots.manage_tabs"));
        this.parent = parent;
        this.player = player;
    }

    @Override
    protected void init() {
        rebuildButtons();
    }

    private void rebuildButtons() {
        clearWidgets();
        iconButtons.clear();
        int left = width / 2 - 150;
        List<CategoryDefinition> categories = categories();
        listBottom = Math.max(61, height - 124);
        int visibleRows = Math.max(1, (listBottom - 38) / ROW_HEIGHT);
        categoryScroll = Math.min(categoryScroll, Math.max(0, categories.size() - visibleRows));

        for (int row = 0; row < visibleRows && row + categoryScroll < categories.size(); row++) {
            int index = row + categoryScroll;
            CategoryDefinition category = categories.get(index);
            int y = 38 + row * ROW_HEIGHT;
            addRenderableWidget(Button.builder(Component.literal(category.displayName()), ignored ->
                            minecraft.setScreen(new CategoryEditorScreen(this, player, category)))
                    .tooltip(Tooltip.create(Component.translatable("gui.stacksnotslots.edit_category_tooltip", category.displayName())))
                    .bounds(left, y, 190, 20).build());
            addRenderableWidget(Button.builder(Component.literal("↑"), ignored -> move(index, -1))
                    .bounds(left + 194, y, 24, 20).build()).active = index > 0;
            addRenderableWidget(Button.builder(Component.literal("↓"), ignored -> move(index, 1))
                    .bounds(left + 220, y, 24, 20).build()).active = index + 1 < categories.size();
            addRenderableWidget(Button.builder(Component.literal("×"), ignored -> remove(category))
                    .tooltip(Tooltip.create(Component.translatable("gui.stacksnotslots.delete_category_tooltip", category.displayName())))
                    .bounds(left + 246, y, 24, 20).build());
        }

        pickerY = height - 104;
        if (selectedHotbar >= 0) addBindingPicker(left, bindableCategories());
        addHotbarButtons(left, height - 80);

        boolean pickupToHotbar = data().pickupIntoHotbar();
        addRenderableWidget(Button.builder(Component.translatable(pickupToHotbar
                        ? "gui.stacksnotslots.pickups_hotbar_on" : "gui.stacksnotslots.pickups_hotbar_off"),
                button -> togglePickupToHotbar()).bounds(left, height - 55, 300, 20).build());

        addRenderableWidget(Button.builder(Component.translatable("gui.stacksnotslots.add_tab"), ignored ->
                        minecraft.setScreen(new CategoryEditorScreen(this, player, null)))
                .bounds(left, height - 30, 96, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.stacksnotslots.reset_defaults"), ignored ->
                        PacketDistributor.sendToServer(new CategoryEditPayload(CategoryEditPayload.Operation.RESET, new net.minecraft.nbt.CompoundTag())))
                .bounds(left + 100, height - 30, 116, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), ignored -> onClose())
                .bounds(left + 220, height - 30, 80, 20).build());
        observedFingerprint = fingerprint();
    }

    private void addHotbarButtons(int left, int y) {
        for (int slot = 0; slot < 9; slot++) {
            HotbarBinding binding = data().hotbar().get(slot);
            CategoryDefinition category = binding.type() == BindingType.CATEGORY ? data().categories().find(binding.target()) : null;
            int targetSlot = slot;
            Component tooltip = category == null
                    ? Component.translatable("gui.stacksnotslots.hotbar_slot_unbound", slot + 1)
                    : Component.translatable("gui.stacksnotslots.hotbar_slot_bound", slot + 1, category.displayName());
            Button button = addRenderableWidget(Button.builder(category == null ? Component.literal(Integer.toString(slot + 1)) : Component.empty(), ignored -> {
                        selectedHotbar = selectedHotbar == targetSlot ? -1 : targetSlot;
                        bindingScroll = 0;
                        rebuildButtons();
                    }).tooltip(Tooltip.create(tooltip)).bounds(left + slot * 33, y, 30, 20).build());
            if (category != null) iconButtons.put(button, category);
        }
    }

    private void addBindingPicker(int left, List<CategoryDefinition> categories) {
        addRenderableWidget(Button.builder(Component.literal("×"), ignored -> bindSelected(null))
                .tooltip(Tooltip.create(Component.translatable("gui.stacksnotslots.clear_hotbar_binding")))
                .bounds(left, pickerY, 20, 20).build());
        int visible = 12;
        bindingScroll = Math.min(bindingScroll, Math.max(0, categories.size() - visible));
        for (int offset = 0; offset < visible && offset + bindingScroll < categories.size(); offset++) {
            CategoryDefinition category = categories.get(offset + bindingScroll);
            Button button = addRenderableWidget(Button.builder(Component.empty(), ignored -> bindSelected(category))
                    .tooltip(Tooltip.create(Component.literal(category.displayName())))
                    .bounds(left + 24 + offset * 23, pickerY, 20, 20).build());
            iconButtons.put(button, category);
        }
    }

    @Override
    public void tick() {
        if (observedFingerprint != fingerprint()) rebuildButtons();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, 15, 0xFFFFFF);
        graphics.drawCenteredString(font, Component.translatable("gui.stacksnotslots.tabs_are_views"), width / 2, 26, 0xA0A0A0);
        if (selectedHotbar >= 0) {
            graphics.drawString(font, Component.translatable("gui.stacksnotslots.choose_hotbar_category", selectedHotbar + 1),
                    width / 2 - 150, pickerY - 11, 0xD0D0D0, false);
        } else {
            graphics.drawString(font, Component.translatable("gui.stacksnotslots.choose_hotbar_slot"),
                    width / 2 - 150, height - 91, 0xD0D0D0, false);
        }
        for (Map.Entry<Button, CategoryDefinition> entry : iconButtons.entrySet()) {
            Button button = entry.getKey();
            graphics.renderItem(CategoryIcons.displayStack(entry.getValue()), button.getX() + (button.getWidth() - 16) / 2, button.getY() + 2);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int left = width / 2 - 150;
        if (selectedHotbar >= 0 && mouseX >= left && mouseX < left + 300 && mouseY >= pickerY && mouseY < pickerY + 20) {
            int maximum = Math.max(0, bindableCategories().size() - 12);
            int next = Math.max(0, Math.min(maximum, bindingScroll - (int)Math.signum(scrollY)));
            if (next != bindingScroll) { bindingScroll = next; rebuildButtons(); }
            return true;
        }
        if (mouseX >= left && mouseX < left + 300 && mouseY >= 38 && mouseY < listBottom) {
            int visibleRows = Math.max(1, (listBottom - 38) / ROW_HEIGHT);
            int maximum = Math.max(0, categories().size() - visibleRows);
            int next = Math.max(0, Math.min(maximum, categoryScroll - (int)Math.signum(scrollY)));
            if (next != categoryScroll) { categoryScroll = next; rebuildButtons(); }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void onClose() { minecraft.setScreen(parent); }

    private com.cappleapple.stacksnotslots.data.PlayerInventoryData data() {
        return player.getData(ModAttachments.PLAYER_DATA);
    }

    private List<CategoryDefinition> categories() { return data().categories().categories(); }
    private List<CategoryDefinition> bindableCategories() {
        return categories().stream().filter(CategoryDefinition::enabled).toList();
    }

    private int fingerprint() {
        int hash = Boolean.hashCode(data().pickupIntoHotbar());
        for (CategoryDefinition category : categories()) hash = 31 * hash + category.hashCode();
        for (int slot = 0; slot < 9; slot++) hash = 31 * hash + data().hotbar().get(slot).hashCode();
        return hash;
    }

    private void bindSelected(CategoryDefinition category) {
        if (selectedHotbar < 0) return;
        HotbarBinding binding = category == null ? HotbarBinding.empty()
                : new HotbarBinding(BindingType.CATEGORY, category.id(), null);
        data().hotbar().set(selectedHotbar, binding);
        PacketDistributor.sendToServer(new HotbarBindPayload(selectedHotbar, binding.type(),
                category == null ? HotbarBindPayload.EMPTY_TARGET : category.id()));
        selectedHotbar = -1;
        rebuildButtons();
    }

    private void togglePickupToHotbar() {
        boolean enabled = !data().pickupIntoHotbar();
        data().setPickupIntoHotbar(enabled);
        PacketDistributor.sendToServer(new PickupToHotbarPayload(enabled));
        rebuildButtons();
    }

    private void remove(CategoryDefinition category) {
        net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
        tag.putString("Id", category.id().toString());
        PacketDistributor.sendToServer(new CategoryEditPayload(CategoryEditPayload.Operation.DELETE, tag));
    }

    private void move(int index, int direction) {
        List<CategoryDefinition> values = categories();
        int otherIndex = index + direction;
        if (otherIndex < 0 || otherIndex >= values.size()) return;
        CategoryDefinition first = values.get(index);
        CategoryDefinition second = values.get(otherIndex);
        send(withOrder(first, second.order()));
        send(withOrder(second, first.order()));
    }

    private static CategoryDefinition withOrder(CategoryDefinition category, int order) {
        return new CategoryDefinition(category.id(), category.displayName(), category.icon(), order, category.includes(), category.excludes(),
                category.pickupLimit(), category.sortMode(), category.enabled(), category.allItems());
    }

    private static void send(CategoryDefinition category) {
        PacketDistributor.sendToServer(new CategoryEditPayload(CategoryEditPayload.Operation.UPSERT, PlayerCategoryData.saveCategory(category)));
    }
}
