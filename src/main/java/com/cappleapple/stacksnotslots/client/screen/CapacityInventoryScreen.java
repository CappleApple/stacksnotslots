package com.cappleapple.stacksnotslots.client.screen;

import com.cappleapple.stacksnotslots.category.CategoryDefinition;
import com.cappleapple.stacksnotslots.config.ClientConfig;
import com.cappleapple.stacksnotslots.client.ContainerInventoryOverlay;
import com.cappleapple.stacksnotslots.data.ModAttachments;
import com.cappleapple.stacksnotslots.network.InventoryViewPreferencesPayload;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.PacketDistributor;

/** Vanilla inventory plus the compact category selector; the shared floating browser renders above it. */
public final class CapacityInventoryScreen extends InventoryScreen {
    private final Player player;
    private Button categorySelector;
    private boolean categoryMenuOpen;
    private int categoryIndex;
    private int categoryMenuCenter;
    private CategoryDefinition hoveredCategory;

    public CapacityInventoryScreen(Player player) {
        super(player);
        this.player = player;
    }

    @Override
    protected void init() {
        super.init();
        int displacement = ContainerInventoryOverlay.displacementOffset(this);
        if (displacement != 0) {
            leftPos += displacement;
            for (var child : children()) {
                if (child instanceof AbstractWidget widget) widget.setX(widget.getX() + displacement);
            }
        }

        ResourceLocation selected = data().selectedCategoryPreference();
        List<CategoryDefinition> categories = categories();
        if (selected != null) {
            for (int index = 0; index < categories.size(); index++) {
                if (categories.get(index).id().equals(selected)) { categoryIndex = index; break; }
            }
        }
        categoryMenuCenter = categoryIndex;
        categorySelector = addRenderableWidget(Button.builder(Component.empty(), ignored -> {
                    categoryMenuOpen = !categoryMenuOpen;
                    categoryMenuCenter = categoryIndex;
                }).bounds(leftPos + ClientConfig.CATEGORY_SELECTOR_X.getAsInt(),
                        topPos + ClientConfig.CATEGORY_SELECTOR_Y.getAsInt(), 20, 20).build());
        updateSelectorTooltip();
    }

    public void focusSearch() { ContainerInventoryOverlay.focusSearch(); }
    public boolean isDrawerOpen() { return ContainerInventoryOverlay.isOpen(); }
    public Rect2i drawerBounds() { return ContainerInventoryOverlay.currentBounds(this); }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        hoveredCategory = null;
        if (categorySelector != null) {
            graphics.renderItem(CategoryIcons.displayStack(currentCategory()), categorySelector.getX() + 2, categorySelector.getY() + 2);
            if (categoryMenuOpen) renderCategoryMenu(graphics, mouseX, mouseY);
        }
        if (hoveredCategory != null) graphics.renderTooltip(font, Component.literal(hoveredCategory.displayName()), mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (categoryMenuOpen && button == 0 && selectCategoryAt(mouseX, mouseY)) return true;
        if (categorySelector != null && categorySelector.mouseClicked(mouseX, mouseY, button)) return true;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (categoryMenuOpen && categorySelector != null
                && mouseX >= categorySelector.getX() + 20 && mouseX < categorySelector.getX() + 42
                && mouseY >= categorySelector.getY() - 42 && mouseY < categorySelector.getY() + 58) {
            List<CategoryDefinition> categories = categories();
            if (!categories.isEmpty()) categoryMenuCenter = Math.floorMod(categoryMenuCenter - (int)Math.signum(scrollY), categories.size());
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private void renderCategoryMenu(GuiGraphics graphics, int mouseX, int mouseY) {
        List<CategoryDefinition> categories = categories();
        if (categories.isEmpty()) return;
        int x = categorySelector.getX() + 22;
        for (int offset = -2; offset <= 2; offset++) {
            CategoryDefinition category = categories.get(Math.floorMod(categoryMenuCenter + offset, categories.size()));
            int y = categorySelector.getY() + offset * 19;
            int alpha = switch (Math.abs(offset)) { case 0 -> 235; case 1 -> 170; default -> 75; };
            boolean hovered = inside(mouseX, mouseY, x, y, 20, 20);
            graphics.fill(x, y, x + 20, y + 20, ((hovered ? 255 : alpha) << 24) | (hovered ? 0x4F72A5 : 0x202020));
            graphics.setColor(1, 1, 1, hovered ? 1 : alpha / 255.0F);
            graphics.renderItem(CategoryIcons.displayStack(category), x + 2, y + 2);
            graphics.setColor(1, 1, 1, 1);
            if (hovered) hoveredCategory = category;
        }
    }

    private boolean selectCategoryAt(double mouseX, double mouseY) {
        List<CategoryDefinition> categories = categories();
        if (categorySelector == null || categories.isEmpty()) return false;
        int x = categorySelector.getX() + 22;
        for (int offset = -2; offset <= 2; offset++) {
            int y = categorySelector.getY() + offset * 19;
            if (!inside(mouseX, mouseY, x, y, 20, 20)) continue;
            categoryIndex = Math.floorMod(categoryMenuCenter + offset, categories.size());
            CategoryDefinition selected = currentCategory();
            categoryMenuOpen = false;
            if (selected != null) {
                data().setSelectedCategoryPreference(selected.id());
                data().setInventorySortPreference(selected.sortMode());
                PacketDistributor.sendToServer(new InventoryViewPreferencesPayload(selected.sortMode(), selected.id()));
            }
            updateSelectorTooltip();
            return true;
        }
        return false;
    }

    private void updateSelectorTooltip() {
        CategoryDefinition category = currentCategory();
        categorySelector.setTooltip(Tooltip.create(Component.literal(category == null
                ? Component.translatable("gui.stacksnotslots.all").getString() : category.displayName())));
    }

    private CategoryDefinition currentCategory() {
        List<CategoryDefinition> categories = categories();
        if (categories.isEmpty()) return null;
        categoryIndex = Math.floorMod(categoryIndex, categories.size());
        return categories.get(categoryIndex);
    }

    private List<CategoryDefinition> categories() {
        return data().categories().categories().stream().filter(CategoryDefinition::enabled).toList();
    }

    private com.cappleapple.stacksnotslots.data.PlayerInventoryData data() {
        return player.getData(ModAttachments.PLAYER_DATA);
    }

    private static boolean inside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }
}
