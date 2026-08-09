package com.cappleapple.stacksnotslots.client.screen;

import com.cappleapple.stacksnotslots.category.CategoryDefinition;
import com.cappleapple.stacksnotslots.client.ContainerInventoryOverlay;
import com.cappleapple.stacksnotslots.config.ClientConfig;
import com.cappleapple.stacksnotslots.data.ModAttachments;
import com.cappleapple.stacksnotslots.network.InventoryViewPreferencesPayload;
import com.cappleapple.stacksnotslots.network.StowMainGridPayload;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;

/** Vanilla inventory plus a draggable, recipe-book-sized category selector. */
public final class CapacityInventoryScreen extends InventoryScreen {
    private static final int SELECTOR_WIDTH = 20;
    private static final int SELECTOR_HEIGHT = 18;
    private static final int MENU_STEP = 19;
    private final Player player;
    private boolean categoryMenuOpen;
    private int categoryIndex;
    private int categoryMenuCenter;
    private int selectorX;
    private int selectorY;
    private boolean draggingSelector;
    private double selectorPressX;
    private double selectorPressY;
    private double selectorDragOffsetX;
    private double selectorDragOffsetY;
    private CategoryDefinition hoveredCategory;
    private Component selectorTooltip;

    public CapacityInventoryScreen(Player player) {
        super(player);
        this.player = player;
    }

    @Override
    protected void init() {
        super.init();
        ResourceLocation selected = data().selectedCategoryPreference();
        List<CategoryDefinition> categories = categories();
        if (selected != null) {
            for (int index = 0; index < categories.size(); index++) {
                if (categories.get(index).id().equals(selected)) { categoryIndex = index; break; }
            }
        }
        categoryMenuCenter = categoryIndex;
        selectorX = clamp(leftPos + ClientConfig.CATEGORY_SELECTOR_X.getAsInt(), 0, width - SELECTOR_WIDTH);
        selectorY = clamp(topPos + ClientConfig.CATEGORY_SELECTOR_Y.getAsInt(), 0, height - SELECTOR_HEIGHT);
    }

    public void focusSearch() { ContainerInventoryOverlay.focusSearch(); }
    public boolean isDrawerOpen() { return ContainerInventoryOverlay.isOpen(); }
    public Rect2i drawerBounds() { return ContainerInventoryOverlay.currentBounds(this); }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        hoveredCategory = null;
        selectorTooltip = null;
        if (categoryMenuOpen) renderCategoryMenu(graphics, mouseX, mouseY);
        renderSelector(graphics, mouseX, mouseY);
        if (hoveredCategory != null) graphics.renderTooltip(font, Component.literal(hoveredCategory.displayName()), mouseX, mouseY);
        else if (selectorTooltip != null) graphics.renderTooltip(font, selectorTooltip, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (ContainerInventoryOverlay.ownsPoint(this, mouseX, mouseY)) return super.mouseClicked(mouseX, mouseY, button);
        if (categoryMenuOpen && button == 0 && selectCategoryAt(mouseX, mouseY)) return true;
        if (button == 0 && inside(mouseX, mouseY, selectorX, selectorY, SELECTOR_WIDTH, SELECTOR_HEIGHT)) {
            if (Screen.hasShiftDown()) {
                PacketDistributor.sendToServer(new StowMainGridPayload());
                categoryMenuOpen = false;
                return true;
            }
            draggingSelector = true;
            selectorPressX = mouseX;
            selectorPressY = mouseY;
            selectorDragOffsetX = mouseX - selectorX;
            selectorDragOffsetY = mouseY - selectorY;
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (!draggingSelector || button != 0) return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
        selectorX = clamp((int)Math.round(mouseX - selectorDragOffsetX), 0, width - SELECTOR_WIDTH);
        selectorY = clamp((int)Math.round(mouseY - selectorDragOffsetY), 0, height - SELECTOR_HEIGHT);
        categoryMenuOpen = false;
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (!draggingSelector || button != 0) return super.mouseReleased(mouseX, mouseY, button);
        draggingSelector = false;
        if (Math.hypot(mouseX - selectorPressX, mouseY - selectorPressY) < 3.0) {
            categoryMenuOpen = !categoryMenuOpen;
            categoryMenuCenter = categoryIndex;
        } else {
            ClientConfig.CATEGORY_SELECTOR_X.set(selectorX - leftPos);
            ClientConfig.CATEGORY_SELECTOR_Y.set(selectorY - topPos);
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (inside(mouseX, mouseY, selectorX, selectorY, SELECTOR_WIDTH, SELECTOR_HEIGHT)) {
            if (!categoryMenuOpen) selectRelative(scrollY > 0 ? -1 : 1);
            return true;
        }
        if (categoryMenuOpen && inside(mouseX, mouseY, selectorX, menuTop(), SELECTOR_WIDTH, MENU_STEP * 5)) {
            List<CategoryDefinition> categories = categories();
            if (!categories.isEmpty()) categoryMenuCenter = Math.floorMod(categoryMenuCenter - (int)Math.signum(scrollY), categories.size());
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private void renderSelector(GuiGraphics graphics, int mouseX, int mouseY) {
        boolean hovered = inside(mouseX, mouseY, selectorX, selectorY, SELECTOR_WIDTH, SELECTOR_HEIGHT);
        graphics.fill(selectorX, selectorY, selectorX + SELECTOR_WIDTH, selectorY + SELECTOR_HEIGHT,
                hovered ? 0xFFF0F0F0 : 0xFFC6C6C6);
        graphics.fill(selectorX + 2, selectorY + 2, selectorX + SELECTOR_WIDTH - 2, selectorY + SELECTOR_HEIGHT - 2,
                categoryMenuOpen ? 0xFF356DA5 : hovered ? 0xFF777777 : 0xFF555555);
        CategoryDefinition displayedCategory = categoryMenuOpen ? categoryAt(categoryMenuCenter) : currentCategory();
        ItemStack icon = Screen.hasShiftDown() ? new ItemStack(Items.STICKY_PISTON) : CategoryIcons.displayStack(displayedCategory);
        graphics.renderItem(icon, selectorX + 2, selectorY + 1);
        if (hovered) {
            selectorTooltip = Screen.hasShiftDown()
                    ? Component.translatable("gui.stacksnotslots.stow_main_grid")
                    : Component.literal(displayedCategory == null
                            ? Component.translatable("gui.stacksnotslots.all").getString()
                            : displayedCategory.displayName());
        }
    }

    private void renderCategoryMenu(GuiGraphics graphics, int mouseX, int mouseY) {
        List<CategoryDefinition> categories = categories();
        if (categories.isEmpty()) return;
        int top = menuTop();
        for (int offset = -2; offset <= 2; offset++) {
            if (offset == 0) continue; // The draggable selector is the carousel's center cell.
            CategoryDefinition category = categories.get(Math.floorMod(categoryMenuCenter + offset, categories.size()));
            int y = top + (offset + 2) * MENU_STEP;
            int alpha = switch (Math.abs(offset)) { case 0 -> 245; case 1 -> 165; default -> 65; };
            boolean hovered = inside(mouseX, mouseY, selectorX, y, SELECTOR_WIDTH, SELECTOR_HEIGHT);
            graphics.fill(selectorX, y, selectorX + SELECTOR_WIDTH, y + SELECTOR_HEIGHT,
                    ((hovered ? 255 : alpha) << 24) | (hovered ? 0x4F72A5 : 0x202020));
            graphics.setColor(1, 1, 1, hovered ? 1 : alpha / 255.0F);
            graphics.renderItem(CategoryIcons.displayStack(category), selectorX + 2, y + 1);
            graphics.setColor(1, 1, 1, 1);
            if (hovered) hoveredCategory = category;
        }
    }

    private boolean selectCategoryAt(double mouseX, double mouseY) {
        List<CategoryDefinition> categories = categories();
        if (categories.isEmpty()) return false;
        int top = menuTop();
        for (int offset = -2; offset <= 2; offset++) {
            int y = top + (offset + 2) * MENU_STEP;
            if (!inside(mouseX, mouseY, selectorX, y, SELECTOR_WIDTH, SELECTOR_HEIGHT)) continue;
            categoryIndex = Math.floorMod(categoryMenuCenter + offset, categories.size());
            categoryMenuOpen = false;
            applyCurrentCategory();
            return true;
        }
        return false;
    }

    private void selectRelative(int direction) {
        if (categories().isEmpty()) return;
        categoryIndex = Math.floorMod(categoryIndex + direction, categories().size());
        categoryMenuCenter = categoryIndex;
        applyCurrentCategory();
    }

    private void applyCurrentCategory() {
        CategoryDefinition selected = currentCategory();
        if (selected == null) return;
        data().setSelectedCategoryPreference(selected.id());
        data().setInventorySortPreference(selected.sortMode());
        PacketDistributor.sendToServer(new InventoryViewPreferencesPayload(selected.sortMode(), selected.id()));
    }

    private int menuTop() { return selectorY - MENU_STEP * 2; }

    private CategoryDefinition categoryAt(int index) {
        List<CategoryDefinition> categories = categories();
        return categories.isEmpty() ? null : categories.get(Math.floorMod(index, categories.size()));
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

    private static int clamp(int value, int minimum, int maximum) { return Math.max(minimum, Math.min(maximum, value)); }
}
