package com.cappleapple.stacksnotslots.client.screen;

import com.cappleapple.stacksnotslots.client.ClientSaveState;
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
    private ClientConfig.BrowserDefaultPlacement defaultPlacement = ClientConfig.BROWSER_DEFAULT_PLACEMENT.get();
    private boolean autoSide = ClientConfig.AUTO_BROWSER_DOCK_SIDE.getAsBoolean();
    private boolean transferOverlay = ClientConfig.BULK_TRANSFER_OVERLAY.getAsBoolean();
    private Button viewButton;
    private Button itemCountButton;
    private Button overallCountButton;
    private Button autoSideButton;
    private Button transferOverlayButton;
    private Button defaultPlacementButton;
    private EditBox columns;
    private EditBox rows;
    private EditBox deadZoneX;
    private EditBox deadZoneY;
    private EditBox overlaySeconds;
    private EditBox handleIcon;
    private EditBox manageIcon;
    private EditBox settingsIcon;

    public InventoryBrowserSettingsScreen(Screen parent) {
        super(Component.translatable("gui.stacksnotslots.browser_settings"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int left = width / 2 - 160;
        viewButton = button(left, 28, 158, ignored -> {
            viewMode = next(viewMode);
            updateButtons();
        }, "tooltip.stacksnotslots.browser_view");
        autoSideButton = button(left + 162, 28, 158, ignored -> {
            autoSide = !autoSide;
            updateButtons();
        }, "tooltip.stacksnotslots.auto_browser_side");

        columns = field(left, 50, 158, Integer.toString(ClientConfig.BROWSER_GRID_COLUMNS.getAsInt()),
                "gui.stacksnotslots.grid_columns", "tooltip.stacksnotslots.grid_columns");
        rows = field(left + 162, 50, 158, Integer.toString(ClientConfig.BROWSER_GRID_ROWS.getAsInt()),
                "gui.stacksnotslots.grid_rows", "tooltip.stacksnotslots.grid_rows");
        itemCountButton = button(left, 72, 320, ignored -> {
            itemCountMode = next(itemCountMode);
            updateButtons();
        }, "tooltip.stacksnotslots.item_count_mode");
        overallCountButton = button(left, 94, 320, ignored -> {
            overallCountMode = next(overallCountMode);
            updateButtons();
        }, "tooltip.stacksnotslots.overall_count_mode");

        deadZoneX = field(left, 116, 158, Integer.toString(ClientConfig.AUTO_DOCK_DEAD_ZONE_X.getAsInt()),
                "gui.stacksnotslots.dead_zone_x", "tooltip.stacksnotslots.dead_zone_x");
        deadZoneY = field(left + 162, 116, 158, Integer.toString(ClientConfig.AUTO_DOCK_DEAD_ZONE_Y.getAsInt()),
                "gui.stacksnotslots.dead_zone_y", "tooltip.stacksnotslots.dead_zone_y");
        transferOverlayButton = button(left, 138, 158, ignored -> {
            transferOverlay = !transferOverlay;
            updateButtons();
        }, "tooltip.stacksnotslots.bulk_overlay");
        overlaySeconds = field(left + 162, 138, 158, Double.toString(ClientConfig.BULK_TRANSFER_OVERLAY_SECONDS.get()),
                "gui.stacksnotslots.overlay_seconds", "tooltip.stacksnotslots.overlay_seconds");

        handleIcon = field(left, 160, 158, ClientConfig.BROWSER_HANDLE_ICON.get(),
                "gui.stacksnotslots.handle_icon", "tooltip.stacksnotslots.handle_icon");
        defaultPlacementButton = button(left + 162, 160, 158, ignored -> {
            defaultPlacement = next(defaultPlacement);
            updateButtons();
        }, "tooltip.stacksnotslots.default_browser_placement");
        manageIcon = field(left, 182, 158, ClientConfig.MANAGE_TABS_ICON.get(),
                "gui.stacksnotslots.manage_icon", "tooltip.stacksnotslots.configurable_icon");
        settingsIcon = field(left + 162, 182, 158, ClientConfig.SETTINGS_ICON.get(),
                "gui.stacksnotslots.settings_icon", "tooltip.stacksnotslots.configurable_icon");
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), ignored -> saveAndClose())
                .bounds(left, Math.max(206, height - 24), 320, 20).build());
        updateButtons();
    }

    private Button button(int x, int y, int width, Button.OnPress press, String tooltipKey) {
        return addRenderableWidget(Button.builder(Component.empty(), press)
                .tooltip(Tooltip.create(Component.translatable(tooltipKey))).bounds(x, y, width, 20).build());
    }

    private EditBox field(int x, int y, int width, String value, String hintKey, String tooltipKey) {
        EditBox box = new EditBox(font, x, y, width, 20, Component.translatable(hintKey));
        box.setValue(value);
        box.setHint(Component.translatable(hintKey));
        box.setTooltip(Tooltip.create(Component.translatable(tooltipKey)));
        box.setMaxLength(128);
        addRenderableWidget(box);
        return box;
    }

    private void updateButtons() {
        viewButton.setMessage(Component.translatable("gui.stacksnotslots.browser_view", display(viewMode)));
        autoSideButton.setMessage(Component.translatable("gui.stacksnotslots.auto_browser_side", onOff(autoSide)));
        itemCountButton.setMessage(Component.translatable("gui.stacksnotslots.item_count_mode", display(itemCountMode)));
        overallCountButton.setMessage(Component.translatable("gui.stacksnotslots.overall_count_mode", display(overallCountMode)));
        transferOverlayButton.setMessage(Component.translatable("gui.stacksnotslots.bulk_overlay", onOff(transferOverlay)));
        defaultPlacementButton.setMessage(Component.translatable(
                "gui.stacksnotslots.default_browser_placement", display(defaultPlacement)));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, 15, 0xFFFFFF);
    }

    private void saveAndClose() {
        ClientConfig.BROWSER_VIEW_MODE.set(viewMode);
        ClientConfig.ITEM_COUNT_MODE.set(itemCountMode);
        ClientConfig.OVERALL_COUNT_MODE.set(overallCountMode);
        ClientConfig.AUTO_BROWSER_DOCK_SIDE.set(autoSide);
        ClientConfig.BROWSER_DEFAULT_PLACEMENT.set(defaultPlacement);
        ClientConfig.BROWSER_HANDLE_X.set(-1);
        ClientConfig.BROWSER_HANDLE_Y.set(-1);
        ClientConfig.BULK_TRANSFER_OVERLAY.set(transferOverlay);
        ClientConfig.BROWSER_GRID_COLUMNS.set(parseBounded(columns.getValue(), 1, 16, 4));
        ClientConfig.BROWSER_GRID_ROWS.set(parseBounded(rows.getValue(), 1, 20, 6));
        ClientConfig.AUTO_DOCK_DEAD_ZONE_X.set(parseBounded(deadZoneX.getValue(), 0, 4096, 48));
        ClientConfig.AUTO_DOCK_DEAD_ZONE_Y.set(parseBounded(deadZoneY.getValue(), 0, 4096, 36));
        ClientConfig.BULK_TRANSFER_OVERLAY_SECONDS.set(parseDouble(overlaySeconds.getValue(), 0.25D, 30.0D, 2.5D));
        ClientConfig.BROWSER_HANDLE_ICON.set(handleIcon.getValue().trim());
        ClientConfig.MANAGE_TABS_ICON.set(manageIcon.getValue().trim());
        ClientConfig.SETTINGS_ICON.set(settingsIcon.getValue().trim());
        ClientSaveState.saveClientSettings();
        onClose();
    }

    @Override public void onClose() { minecraft.setScreen(parent); }

    private static int parseBounded(String value, int minimum, int maximum, int fallback) {
        try { return Math.max(minimum, Math.min(maximum, Integer.parseInt(value.trim()))); }
        catch (NumberFormatException ignored) { return fallback; }
    }

    private static double parseDouble(String value, double minimum, double maximum, double fallback) {
        try { return Math.max(minimum, Math.min(maximum, Double.parseDouble(value.trim()))); }
        catch (NumberFormatException ignored) { return fallback; }
    }

    private static Component onOff(boolean value) { return Component.translatable(value ? "options.on" : "options.off"); }

    private static Component display(Enum<?> value) {
        String text = value.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        return Component.literal(Character.toUpperCase(text.charAt(0)) + text.substring(1));
    }

    private static <T extends Enum<T>> T next(T value) {
        T[] values = value.getDeclaringClass().getEnumConstants();
        return values[(value.ordinal() + 1) % values.length];
    }
}
