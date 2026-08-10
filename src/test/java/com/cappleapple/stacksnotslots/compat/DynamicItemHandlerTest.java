package com.cappleapple.stacksnotslots.compat;

import com.cappleapple.stacksnotslots.api.compat.DynamicItemHandler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cappleapple.stacksnotslots.api.inventory.DynamicCapacityInventory;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class DynamicItemHandlerTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void appendSlotExpandsInsteadOfDefiningCapacity() {
        DynamicCapacityInventory inventory = new DynamicCapacityInventory(() -> 512);
        DynamicItemHandler handler = new DynamicItemHandler(inventory);
        assertEquals(1, handler.getSlots());
        assertTrue(handler.insertItem(0, new ItemStack(Items.STONE, 64), false).isEmpty());
        assertEquals(2, handler.getSlots());
        assertTrue(handler.insertItem(1, new ItemStack(Items.DIRT, 64), false).isEmpty());
        assertEquals(3, handler.getSlots());
        assertEquals(64, handler.extractItem(0, 64, false).getCount());
        assertEquals(3, handler.getSlots());
        assertTrue(handler.getStackInSlot(0).isEmpty());
        assertEquals(Items.DIRT, handler.getStackInSlot(1).getItem());
    }

    @Test
    void slotSpecificInsertionDoesNotMergeIntoAnotherIndex() {
        DynamicCapacityInventory inventory = new DynamicCapacityInventory(() -> 512);
        DynamicItemHandler handler = new DynamicItemHandler(inventory);
        assertTrue(handler.insertItem(0, new ItemStack(Items.STONE, 8), false).isEmpty());
        assertTrue(handler.insertItem(1, new ItemStack(Items.STONE, 4), false).isEmpty());
        assertEquals(8, handler.getStackInSlot(0).getCount());
        assertEquals(4, handler.getStackInSlot(1).getCount());
    }
}
