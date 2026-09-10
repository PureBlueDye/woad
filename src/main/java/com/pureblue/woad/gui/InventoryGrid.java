package com.pureblue.woad.gui;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared geometry for the "inventory command buttons" feature.
 *
 * <p>Buttons live on an 18px grid aligned with the player-inventory slots. A grid cell holds a
 * 16&times;16 button at {@code (guiX + 8 + col*18, guiY + 8 + row*18)} — exactly where a slot would
 * sit. Cells that overlap a real slot (armor, crafting, off-hand, main inventory, hotbar) are
 * forbidden; everything else (gaps and a margin around the GUI) is placeable.
 */
public final class InventoryGrid {

    public static final int CELL = 18;
    public static final int BTN = 16;
    public static final int OFFSET = 8; // first slot's pixel offset inside the GUI

    // Grid extent: the 176x166 background is roughly cols 0..9 / rows 0..8; we add a margin around.
    public static final int COL_MIN = -3;
    public static final int COL_MAX = 11;
    public static final int ROW_MIN = -2;
    public static final int ROW_MAX = 10;

    /** Standard player-inventory slot top-lefts, relative to the GUI origin (each 16x16). */
    private static final int[][] SLOTS = buildSlots();

    private InventoryGrid() {}

    /** Standard player-inventory slot top-lefts (relative to the GUI origin), for the editor. */
    public static int[][] slots() {
        return SLOTS;
    }

    public static int cellX(int guiX, int col) {
        return guiX + OFFSET + col * CELL;
    }

    public static int cellY(int guiY, int row) {
        return guiY + OFFSET + row * CELL;
    }

    /** True if a button at this cell would overlap a real inventory slot (so it's not placeable). */
    public static boolean isSlotCell(int col, int row) {
        int bx = OFFSET + col * CELL;
        int by = OFFSET + row * CELL;
        for (int[] s : SLOTS) {
            if (bx < s[0] + BTN && bx + BTN > s[0] && by < s[1] + BTN && by + BTN > s[1]) {
                return true;
            }
        }
        return false;
    }

    private static int[][] buildSlots() {
        List<int[]> s = new ArrayList<>();
        // Armor.
        s.add(new int[]{8, 8});
        s.add(new int[]{8, 26});
        s.add(new int[]{8, 44});
        s.add(new int[]{8, 62});
        // Off-hand.
        s.add(new int[]{77, 62});
        // Crafting 2x2 + result.
        s.add(new int[]{98, 18});
        s.add(new int[]{116, 18});
        s.add(new int[]{98, 36});
        s.add(new int[]{116, 36});
        s.add(new int[]{154, 28});
        // Main inventory (9x3).
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 9; c++) {
                s.add(new int[]{8 + c * 18, 84 + r * 18});
            }
        }
        // Hotbar.
        for (int c = 0; c < 9; c++) {
            s.add(new int[]{8 + c * 18, 142});
        }
        return s.toArray(new int[0][]);
    }
}
