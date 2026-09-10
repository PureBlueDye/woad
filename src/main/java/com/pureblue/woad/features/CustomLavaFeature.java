package com.pureblue.woad.features;

import com.pureblue.woad.core.Feature;
import com.pureblue.woad.gui.LavaPanel;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;

/**
 * Recolors lava (or renders it as water) — the former CustomLava mod, folded into Woad. It has
 * no on/off toggle: the whole editor lives directly in this feature's panel in the Woad menu.
 */
public class CustomLavaFeature extends Feature {

    private final LavaPanel panel = new LavaPanel();

    public CustomLavaFeature() {
        super("custom_lava", "Custom Lava",
                "Recolor lava or render it as water. Pick a base color, build a palette, type a hex, "
                        + "or switch the lava/water texture, then Apply.",
                true);
    }

    @Override
    public boolean hasToggle() {
        return false;
    }

    @Override
    public boolean hasCustomPanel() {
        return true;
    }

    @Override
    public void renderPanel(GuiGraphicsExtractor ctx, Screen parent, int x, int top, int right, int bottom, int mouseX, int mouseY) {
        panel.render(ctx, x, top, right, bottom, mouseX, mouseY);
    }

    @Override
    public boolean panelMouseClicked(Screen parent, double mouseX, double mouseY, int button) {
        return panel.mouseClicked(parent, mouseX, mouseY, button);
    }

    @Override
    public void panelRemoved() {
        panel.dispose();
    }
}
