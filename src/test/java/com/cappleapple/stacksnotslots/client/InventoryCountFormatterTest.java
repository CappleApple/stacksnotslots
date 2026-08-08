package com.cappleapple.stacksnotslots.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.cappleapple.stacksnotslots.api.LogicalInventoryEntry;
import com.cappleapple.stacksnotslots.config.ClientConfig;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class InventoryCountFormatterTest {
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }

    @Test
    void compactAndStackModesUseExpectedUnits() {
        LogicalInventoryEntry stone = new LogicalInventoryEntry(new ItemStack(Items.STONE), 1_472, 23);
        assertEquals("1.5k", InventoryCountFormatter.item(stone, 1_728, ClientConfig.ItemCountMode.COMPACT));
        assertEquals("23S", InventoryCountFormatter.item(stone, 1_728, ClientConfig.ItemCountMode.STACKS));
        assertEquals("23s+0", InventoryCountFormatter.item(stone, 1_728, ClientConfig.ItemCountMode.STACKS_REMAINDER));

        LogicalInventoryEntry swords = new LogicalInventoryEntry(new ItemStack(Items.IRON_SWORD), 3, 3);
        assertEquals("3S", InventoryCountFormatter.item(swords, 1_728, ClientConfig.ItemCountMode.STACKS));
        assertEquals("3s+0", InventoryCountFormatter.item(swords, 1_728, ClientConfig.ItemCountMode.STACKS_REMAINDER));
    }

    @Test
    void percentageAndOverallModesUseCapacity() {
        LogicalInventoryEntry stone = new LogicalInventoryEntry(new ItemStack(Items.STONE), 864, 14);
        assertEquals("50%", InventoryCountFormatter.item(stone, 1_728, ClientConfig.ItemCountMode.PERCENTAGE));
        assertEquals("27S / 27S", InventoryCountFormatter.overall(1_728, 1_728, ClientConfig.OverallCountMode.STACKS));
        assertEquals("50%", InventoryCountFormatter.overall(864, 1_728, ClientConfig.OverallCountMode.PERCENTAGE));
    }
}
