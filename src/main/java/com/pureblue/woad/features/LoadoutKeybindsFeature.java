package com.pureblue.woad.features;

import com.pureblue.woad.core.Feature;
import com.pureblue.woad.core.setting.KeybindSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.inventory.ContainerInput;

import java.util.regex.Pattern;

/**
 * Keybinds for the Hypixel Loadouts menu ("(x/y) Loadouts"), like NoammAddons' wardrobe keybinds.
 * While that menu is open, pressing a bound key clicks the matching loadout slot (the 3x4 block on
 * the right of the chest). Slot positions are fixed to that layout; page changes reuse the same
 * on-screen positions.
 */
public class LoadoutKeybindsFeature extends Feature {

    private static final Pattern TITLE = Pattern.compile("^\\(\\d+/\\d+\\) Loadouts$");

    /** The 12 loadout slots: a 3-wide x 4-tall block (columns 5-7, rows 1-4) of a 54-slot chest. */
    private static final int[] SLOTS = {14, 15, 16, 23, 24, 25, 32, 33, 34, 41, 42, 43};

    private final KeybindSetting[] binds = new KeybindSetting[SLOTS.length];

    public LoadoutKeybindsFeature() {
        super("loadout_keybinds", "Loadout Keybinds",
                "Press a bound key in the Loadouts menu to select that loadout.",
                true);
        for (int i = 0; i < SLOTS.length; i++) {
            binds[i] = addSetting(new KeybindSetting("Loadout " + (i + 1), ""));
        }
    }

    /** True if the given (formatting-stripped) screen title is the Loadouts menu. */
    public static boolean isLoadoutMenu(String strippedTitle) {
        return TITLE.matcher(strippedTitle).matches();
    }

    /** Called on a key press while the Loadouts menu is open; returns true if it clicked a loadout. */
    public boolean onKeyInMenu(AbstractContainerScreen<?> screen, int keyCode) {
        if (!isEnabled()) return false;
        for (int i = 0; i < binds.length; i++) {
            if (binds[i].matches(keyCode)) {
                clickSlot(screen, SLOTS[i]);
                return true;
            }
        }
        return false;
    }

    private static void clickSlot(AbstractContainerScreen<?> screen, int slot) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gameMode == null) return;
        if (slot >= screen.getMenu().slots.size()) return;
        ItemStack stack = screen.getMenu().getSlot(slot).getItem();
        if (stack.isEmpty()) return; // don't click empty slots (matches NoammAddons)
        mc.gameMode.handleContainerInput(screen.getMenu().containerId, slot, 0, ContainerInput.PICKUP, mc.player);
    }
}
