package com.cappleapple.stacksnotslots.client;

import com.cappleapple.stacksnotslots.StacksNotSlots;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.ScreenEvent;

/** Temporary, narrowly scoped diagnostics for custom container-screen interoperability. */
public final class ContainerInteractionTrace {
    private static final String SOPHISTICATED_PACKAGE = "net.p3pp3rf1y.sophisticated";
    private static final AtomicLong NEXT_INTERACTION = new AtomicLong();
    private static long interaction;

    private ContainerInteractionTrace() {}

    public static void rawMouse(int button, int action, int modifiers, boolean browserHandled) {
        Screen screen = Minecraft.getInstance().screen;
        if (!isSophisticated(screen)) return;
        if (action == 1) interaction = NEXT_INTERACTION.incrementAndGet();
        StacksNotSlots.LOGGER.info(
                "[SNS-COMPAT {}] raw mouse button={} action={} modifiers={} browserHandled={} {}",
                interaction, button, action, modifiers, browserHandled, screenState(screen));
    }

    public static void mousePressedPre(ScreenEvent.MouseButtonPressed.Pre event) {
        if (!isSophisticated(event.getScreen())) return;
        StacksNotSlots.LOGGER.info(
                "[SNS-COMPAT {}] screen press pre button={} x={} y={} canceled={} {}",
                interaction, event.getButton(), event.getMouseX(), event.getMouseY(), event.isCanceled(), screenState(event.getScreen()));
    }

    public static void mousePressedPost(ScreenEvent.MouseButtonPressed.Post event) {
        if (!isSophisticated(event.getScreen())) return;
        StacksNotSlots.LOGGER.info(
                "[SNS-COMPAT {}] screen press post button={} handled={} result={} finalHandled={} {}",
                interaction, event.getButton(), event.wasClickHandled(), event.getResult(), event.getClickResult(), screenState(event.getScreen()));
    }

    public static void outgoingClick(ServerboundContainerClickPacket packet) {
        Screen screen = Minecraft.getInstance().screen;
        if (!isSophisticated(screen)) return;
        StacksNotSlots.LOGGER.info(
                "[SNS-COMPAT {}] outbound click container={} state={} slot={} button={} type={} carried={} changedSlots={} {}",
                interaction, packet.getContainerId(), packet.getStateId(), packet.getSlotNum(), packet.getButtonNum(),
                packet.getClickType(), stack(packet.getCarriedItem()), packet.getChangedSlots().size(), screenState(screen));
    }

    public static void incomingSlot(ClientboundContainerSetSlotPacket packet) {
        Screen screen = Minecraft.getInstance().screen;
        if (!isSophisticated(screen)) return;
        StacksNotSlots.LOGGER.info(
                "[SNS-COMPAT {}] inbound slot container={} state={} slot={} item={} {}",
                interaction, packet.getContainerId(), packet.getStateId(), packet.getSlot(), stack(packet.getItem()), screenState(screen));
    }

    public static void incomingContent(ClientboundContainerSetContentPacket packet) {
        Screen screen = Minecraft.getInstance().screen;
        if (!isSophisticated(screen)) return;
        StacksNotSlots.LOGGER.info(
                "[SNS-COMPAT {}] inbound content container={} state={} items={} carried={} {}",
                interaction, packet.getContainerId(), packet.getStateId(), packet.getItems().size(),
                stack(packet.getCarriedItem()), screenState(screen));
    }

    private static boolean isSophisticated(Screen screen) {
        return screen != null && screen.getClass().getName().startsWith(SOPHISTICATED_PACKAGE);
    }

    private static String screenState(Screen screen) {
        if (!(screen instanceof AbstractContainerScreen<?> containerScreen)) {
            return "screen=" + (screen == null ? "none" : screen.getClass().getName());
        }
        AbstractContainerMenu menu = containerScreen.getMenu();
        Slot hovered = containerScreen.getSlotUnderMouse();
        String hoveredState = hovered == null
                ? "none"
                : hovered.index + "/container:" + hovered.getContainerSlot() + "/" + stack(hovered.getItem());
        return "screen=" + screen.getClass().getName()
                + " menu=" + menu.getClass().getName()
                + " container=" + menu.containerId
                + " state=" + menu.getStateId()
                + " hovered=" + hoveredState
                + " carried=" + stack(menu.getCarried());
    }

    private static String stack(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return "empty";
        return stack.getItem() + "x" + stack.getCount();
    }
}
