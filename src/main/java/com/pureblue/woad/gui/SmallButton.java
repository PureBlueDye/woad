package com.pureblue.woad.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * A flat labelled button drawn by a feature's extra controls, in the menu's style.
 *
 * <p>{@link #draw} stores its bounds into the caller's {@code rect} so {@link #hit} can test the
 * same area on the next click.
 */
public final class SmallButton {

    public static final int HEIGHT = 16;

    private SmallButton() {}

    /** Draws the button and records its bounds into {@code rect} as {x1, y1, x2, y2}. */
    public static void draw(GuiGraphicsExtractor ctx, int[] rect, int x, int y, int w, String label,
                            int mouseX, int mouseY) {
        boolean hovered = mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + HEIGHT;
        ctx.fill(x, y, x + w, y + HEIGHT, hovered ? 0xFF3A3A44 : 0xFF26262C);
        ctx.fill(x, y, x + w, y + 1, 0xFF45454F);
        ctx.fill(x, y + HEIGHT - 1, x + w, y + HEIGHT, 0xFF45454F);
        ctx.fill(x, y, x + 1, y + HEIGHT, 0xFF45454F);
        ctx.fill(x + w - 1, y, x + w, y + HEIGHT, 0xFF45454F);
        ctx.text(Minecraft.getInstance().font, label, x + 6, y + 4, 0xFFE8E8EC, false);
        rect[0] = x;
        rect[1] = y;
        rect[2] = x + w;
        rect[3] = y + HEIGHT;
    }

    /** True when the point falls inside the bounds recorded by {@link #draw}. */
    public static boolean hit(int[] rect, double mx, double my) {
        return mx >= rect[0] && mx <= rect[2] && my >= rect[1] && my <= rect[3];
    }
}
