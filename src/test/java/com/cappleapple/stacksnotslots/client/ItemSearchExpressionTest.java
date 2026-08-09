package com.cappleapple.stacksnotslots.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

class ItemSearchExpressionTest {
    private static final ItemStack DIAMOND_SWORD = new ItemStack(Items.DIAMOND_SWORD);

    @Test
    void prefixesSelectDistinctSearchModes() {
        assertEquals(ItemSearchExpression.Mode.MOD, ItemSearchExpression.parse("@mine").mode());
        assertEquals(ItemSearchExpression.Mode.TAG, ItemSearchExpression.parse("#tools").mode());
        assertEquals(ItemSearchExpression.Mode.TOOLTIP, ItemSearchExpression.parse("^damage").mode());
        assertEquals(ItemSearchExpression.Mode.REGEX, ItemSearchExpression.parse("/diamond.*sword").mode());
        assertEquals(ItemSearchExpression.Mode.TOOLTIP_REGEX, ItemSearchExpression.parse("^/damage.*7").mode());
    }

    @Test
    void normalSearchDoesNotInspectTooltipText() {
        ItemSearchExpression search = ItemSearchExpression.parse("seven attack damage");
        assertFalse(search.requiresTooltip());
        assertFalse(search.matches(DIAMOND_SWORD, () -> "seven attack damage"));
    }

    @Test
    void caretSearchMatchesOnlyTooltipText() {
        ItemSearchExpression search = ItemSearchExpression.parse("^attack damage");
        assertTrue(search.requiresTooltip());
        assertTrue(search.matches(DIAMOND_SWORD, () -> "7 attack damage"));
        assertFalse(search.matches(DIAMOND_SWORD, () -> "durability"));
    }

    @Test
    void regexSearchIsCaseInsensitiveAndAcceptsAnOptionalClosingSlash() {
        ItemSearchExpression open = ItemSearchExpression.parse("/MINECRAFT:DIAMOND_(SWORD|PICKAXE)");
        ItemSearchExpression delimited = ItemSearchExpression.parse("/MINECRAFT:DIAMOND_(SWORD|PICKAXE)/");
        assertTrue(open.matches(DIAMOND_SWORD, () -> ""));
        assertTrue(delimited.matches(DIAMOND_SWORD, () -> ""));
    }

    @Test
    void tooltipRegexCombinesBothPrefixes() {
        ItemSearchExpression search = ItemSearchExpression.parse("^/damage\\s+[0-9]+/");
        assertTrue(search.matches(DIAMOND_SWORD, () -> "attack damage 7"));
        assertFalse(search.matches(DIAMOND_SWORD, () -> "durability 1561"));
    }

    @Test
    void invalidRegexIsSafeAndMatchesNothing() {
        ItemSearchExpression search = ItemSearchExpression.parse("/[unterminated");
        assertFalse(search.valid());
        assertFalse(search.matches(DIAMOND_SWORD, () -> "anything"));
    }
}
