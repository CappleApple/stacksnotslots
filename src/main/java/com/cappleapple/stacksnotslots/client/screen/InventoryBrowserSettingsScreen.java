package com.cappleapple.stacksnotslots.client.screen;

import com.cappleapple.stacksnotslots.config.ClientConfig;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class InventoryBrowserSettingsScreen extends Screen {
    private final Screen parent;
    private ClientConfig.BrowserViewMode viewMode = ClientConfig.BROWSER_VIEW_MODE.get();
    private ClientConfig.ItemCountMode itemCountMode = ClientConfig.ITEM_COUNT_MODE.get();
    private ClientConfig.OverallCountMode overallCountMode = ClientConfig.OVERALL_COUNT_MODE.get();
    private boolean displace = ClientConfig.BROWSER_DISPLACES_CONTAINER.getAsBoolean();
    private Button displaceButton;
    private Button viewButton;
    private Button itemCountButton;
    private Button overallCountButton;
    private EditBox columns;
    private EditBox rows;
    private EditBox manageIcon;
    private EditBox settingsIcon;
    private EditBox viewIcon;

    public InventoryBrowserSettingsScreen(Screen parent) {
        super(Component.translatable("gui.stacksnotslots.browser_settings"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int left = width / 2 - 160;
        displaceButton = addRenderableWidget(Button.builder(Component.empty(), ignored -> {
                    displace = !displace;
                    updateButtons();
                }).tooltip(Tooltip.create(Component.translatable("tooltip.stacksnotslots.displace_browser")))
                .bounds(left, 28, 320, 20).build());
        viewButton = addRenderableWidget(Button.builder(Component.empty(), ignored -> {
                    viewMode = next(viewMode);
                    updateButtons();
                }).bounds(left, 50, 320, 20).build());
        columns = field(left, 72, 156, Integer.toString(ClientConfig.BROWSER_GRID_COLUMNS.getAsInt()), "gui.stacksnotslots.grid_columns");
        rows = field(left + 164, 72, 156, Integer.toString(ClientConfig.BROWSER_GRID_ROWS.getAsInt()), "gui.stacksnotslots.grid_rows");
        itemCountButton = addRenderableWidget(Button.builder(Component.empty(), ignored -> {
                    itemCountMode = next(itemCountMode);
                    updateButtons();
                }).bounds(left, 94, 320, 20).build());
        overallCountButton = addRenderableWidget(Button.builder(Component.empty(), ignored -> {
                    overallCountMode = next(overallCountMode);
                    updateButtons();
                }).bounds(left, 116, 320, 20).build());
        manageIcon = field(left, 150, 320, ClientConfig.MANAGE_TABS_ICON.get(), "gui.stacksnotslots.manage_icon");
        settingsIcon = field(left, 172, 320, ClientConfig.SETTINGS_ICON.get(), "gui.stacksnotslots.settings_icon");
        viewIcon = field(left, 194, 320, ClientConfig.VIEW_MODE_ICON.get(), "gui.stacksnotslots.view_icon");
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), ignored -> saveAndClose())
                .bounds(left, Math.max(216, height - 24), 320, 20).build());
        updateButtons();
    }

    private EditBox field(int x, int y, int width, String value, String hint) {
        EditBox box = new EditBox(font, x, y, width, 20, Component.translatable(hint));
        box.setValue(value);
        box.setHint(Component.translatable(hint));
        box.setMaxLength(128);
        addRenderableWidget(box);
        return box;
    }

    private void updateButtons() {
        displaceButton.setMessage(Component.translatable("gui.stacksnotslots.displace_browser", onOff(displace)));
        viewButton.setMessage(Component.translatable("gui.stacksnotslots.browser_view", display(viewMode)));
        itemCountButton.setMessage(Component.translatable("gui.stacksnotslots.item_count_mode", display(itemCountMode)));
        overallCountButton.setMessage(Component.translatable("gui.stacksnotslots.overall_count_mode", display(overallCountMode)));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, 15, 0xFFFFFF);
        graphics.drawString(font, Component.translatable("gui.stacksnotslots.browser_icons_help"), width / 2 - 160, 139, 0xA0A0A0, false);
    }

    private void saveAndClose() {
        ClientConfig.BROWSER_DISPLACES_CONTAINER.set(displace);
        ClientConfig.BROWSER_VIEW_MODE.set(viewMode);
        ClientConfig.ITEM_COUNT_MODE.set(itemCountMode);
        ClientConfig.OVERALL_COUNT_MODE.set(overallCountMode);
        ClientConfig.BROWSER_GRID_COLUMNS.set(parseBounded(columns.getValue(), 1, 16, 4));
        ClientConfig.BROWSER_GRID_ROWS.set(parseBounded(rows.getValue(), 1, 20, 6));
        ClientConfig.MANAGE_TABS_ICON.set(manageIcon.getValue().trim());
        ClientConfig.SETTINGS_ICON.set(settingsIcon.getValue().trim());
        ClientConfig.VIEW_MODE_ICON.set(viewIcon.getValue().trim());
        onClose();
    }

    @Override public void onClose() { minecraft.setScreen(parent); }

    private static int parseBounded(String value, int minimum, int maximum, int fallback) {
        try { return Math.max(minimum, Math.min(maximum, Integer.parseInt(value.trim()))); }
        catch (NumberFormatException ignored) { return fallback; }
    }

    private static Component onOff(boolean value) {
        return Component.translatable(value ? "options.on" : "options.off");
    }

    private static Component display(Enum<?> value) {
        String text = value.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        return Component.literal(Character.toUpperCase(text.charAt(0)) + text.substring(1));
    }

    private static <T extends Enum<T>> T next(T value) {
        T[] values = value.getDeclaringClass().getEnumConstants();
        return values[(value.ordinal() + 1) % values.length];
    }
}
