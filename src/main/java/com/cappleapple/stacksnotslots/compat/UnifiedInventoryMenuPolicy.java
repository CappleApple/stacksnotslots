package com.cappleapple.stacksnotslots.compat;

/** Rules for vanilla's fixed player-inventory menu when both storage regions are one logical view. */
public final class UnifiedInventoryMenuPolicy {
    public static final int MAIN_STORAGE_START = 9;
    public static final int HOTBAR_END = 45;

    private UnifiedInventoryMenuPolicy() {}

    /**
     * A main-storage to hotbar (or reverse) shift-click has no destination in a unified inventory.
     * Auto-equipping armor/offhand remains a real transfer and must continue through vanilla.
     */
    public static boolean suppressStorageToStorageShiftClick(int menuIndex, boolean canAutoEquip) {
        return menuIndex >= MAIN_STORAGE_START && menuIndex < HOTBAR_END && !canAutoEquip;
    }
}
