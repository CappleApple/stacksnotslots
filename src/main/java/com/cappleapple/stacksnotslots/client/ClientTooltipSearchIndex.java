package com.cappleapple.stacksnotslots.client;

import com.cappleapple.stacksnotslots.config.ClientConfig;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/** Client-local tooltip text cache used only by explicit caret-prefixed searches. */
public final class ClientTooltipSearchIndex {
    private static final Map<SearchIdentity, String> CACHE = new HashMap<>();

    private ClientTooltipSearchIndex() {}

    public static String text(ItemStack stack) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!ClientConfig.TOOLTIP_INDEXING.getAsBoolean() || minecraft.player == null || stack.isEmpty()) return "";
        return CACHE.computeIfAbsent(new SearchIdentity(stack), ignored ->
                stack.getTooltipLines(Item.TooltipContext.of(minecraft.player.level()), minecraft.player,
                                TooltipFlag.Default.NORMAL).stream().skip(1)
                        .map(Component::getString)
                        .map(line -> line.toLowerCase(Locale.ROOT))
                        .reduce("", (left, right) -> left.isEmpty() ? right : left + '\n' + right));
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
