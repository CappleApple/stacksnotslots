package com.cappleapple.stacksnotslots.client.screen;

import com.cappleapple.stacksnotslots.category.CategoryDefinition;
import com.cappleapple.stacksnotslots.category.PlayerCategoryData;
import com.cappleapple.stacksnotslots.data.ModAttachments;
import com.cappleapple.stacksnotslots.network.CategoryEditPayload;
import com.cappleapple.stacksnotslots.network.HotbarBindPayload;
import com.cappleapple.stacksnotslots.hotbar.BindingType;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.PacketDistributor;

public final class CategoryManagerScreen extends Screen {
    private static final int VISIBLE_ROWS = 8;
    private final Screen parent;
    private final Player player;
    private int scroll;
    private int observedCategoryFingerprint;

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
        int left = width / 2 - 150;
        List<CategoryDefinition> categories = categories();
        scroll = Math.min(scroll, Math.max(0, categories.size() - VISIBLE_ROWS));
        for (int row = 0; row < VISIBLE_ROWS && row + scroll < categories.size(); row++) {
            int index = row + scroll;
            CategoryDefinition category = categories.get(index);
            int y = 38 + row * 23;
            addRenderableWidget(Button.builder(Component.literal(category.displayName()), ignored -> minecraft.setScreen(new CategoryEditorScreen(this, player, category)))
                    .bounds(left, y, 160, 20).build());
            addRenderableWidget(Button.builder(Component.literal("B"), ignored -> bind(category)).bounds(left + 164, y, 26, 20).build());
            addRenderableWidget(Button.builder(Component.literal("↑"), ignored -> move(index, -1)).bounds(left + 194, y, 24, 20).build()).active = index > 0;
            addRenderableWidget(Button.builder(Component.literal("↓"), ignored -> move(index, 1)).bounds(left + 220, y, 24, 20).build()).active = index + 1 < categories.size();
            addRenderableWidget(Button.builder(Component.literal("×"), ignored -> remove(category)).bounds(left + 246, y, 24, 20).build());
        }
        addRenderableWidget(Button.builder(Component.translatable("gui.stacksnotslots.add_tab"), ignored -> minecraft.setScreen(new CategoryEditorScreen(this, player, null)))
                .bounds(left, height - 30, 96, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.stacksnotslots.reset_defaults"), ignored -> {
            PacketDistributor.sendToServer(new CategoryEditPayload(CategoryEditPayload.Operation.RESET, new net.minecraft.nbt.CompoundTag()));
        }).bounds(left + 100, height - 30, 116, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), ignored -> onClose()).bounds(left + 220, height - 30, 80, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.stacksnotslots.unbind_selected"), ignored ->
                PacketDistributor.sendToServer(new HotbarBindPayload(player.getInventory().selected, BindingType.EMPTY, HotbarBindPayload.EMPTY_TARGET)))
                .bounds(left, height - 80, 140, 20).build());
        observedCategoryFingerprint = categoryFingerprint();
    }

    @Override
    public void tick() {
        if (observedCategoryFingerprint != categoryFingerprint()) rebuildButtons();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, 15, 0xFFFFFF);
        graphics.drawCenteredString(font, Component.translatable("gui.stacksnotslots.tabs_are_views"), width / 2, 26, 0xA0A0A0);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int maximum = Math.max(0, categories().size() - VISIBLE_ROWS);
        int next = Math.max(0, Math.min(maximum, scroll - (int)Math.signum(scrollY)));
        if (next != scroll) { scroll = next; rebuildButtons(); }
        return true;
    }

    @Override
    public void onClose() { minecraft.setScreen(parent); }

    private List<CategoryDefinition> categories() { return player.getData(ModAttachments.PLAYER_DATA).categories().categories(); }

    private int categoryFingerprint() {
        int hash = 1;
        for (CategoryDefinition category : categories()) {
            hash = 31 * hash + category.hashCode();
        }
        return hash;
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

    private void bind(CategoryDefinition category) {
        PacketDistributor.sendToServer(new HotbarBindPayload(player.getInventory().selected, BindingType.CATEGORY, category.id()));
    }

    private static CategoryDefinition withOrder(CategoryDefinition category, int order) {
        return new CategoryDefinition(category.id(), category.displayName(), category.icon(), order, category.includes(), category.excludes(),
                category.pickupLimit(), category.sortMode(), category.enabled(), category.allItems());
    }

    private static void send(CategoryDefinition category) {
        PacketDistributor.sendToServer(new CategoryEditPayload(CategoryEditPayload.Operation.UPSERT, PlayerCategoryData.saveCategory(category)));
    }
}
