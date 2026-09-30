package com.pureblue.woad.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Static surfaces: panels, cards, dividers, the screen backdrop, progress bars, inventory slots
 * and status pills. None of them take input, so they need no state of their own.
 */
public final class UiPanel {

    private UiPanel() {}

    /**
     * The main glass panel of a screen: a translucent diagonal gradient the blurred world shows
     * through, and a thin edge lit from above. No drop shadow: under a see-through panel it showed
     * as a band sticking out below.
     */
    public static void panel(GuiGraphicsExtractor ctx, float x, float y, float w, float h) {
        float r = Theme.RADIUS_PANEL;
        Draw.roundRectDiagonal(ctx, x, y, w, h, r, Theme.PANEL_TOP, Theme.PANEL_BOTTOM);
        Draw.outline(ctx, x, y, w, h, r, 1f, 0x4D94B0DC, Theme.LINE);
        Draw.outline(ctx, x + 1, y + 1, w - 2, h - 2, r - 1, 1f, Theme.PANEL_HIGHLIGHT, 0);
    }

    /** A card inside a panel. */
    public static void card(GuiGraphicsExtractor ctx, float x, float y, float w, float h) {
        float r = Theme.RADIUS_CARD;
        Draw.roundRect(ctx, x, y, w, h, r, Draw.withAlpha(Theme.CARD, 0.8f));
        Draw.outline(ctx, x, y, w, h, r, 1f, Theme.LINE);
    }

    /** A recessed well: behind lists, grids and previews. */
    public static void inset(GuiGraphicsExtractor ctx, float x, float y, float w, float h, float r) {
        Draw.roundRect(ctx, x, y, w, h, r, Theme.INSET);
        Draw.outline(ctx, x, y, w, h, r, 1f, Theme.LINE);
    }

    /** A hairline, one physical pixel thick at any GUI scale. */
    public static void hairline(GuiGraphicsExtractor ctx, float x0, float x1, float y) {
        Draw.rect(ctx, x0, y, x1, y + 1f / Draw.guiScale(), Theme.LINE);
    }

    public static void vHairline(GuiGraphicsExtractor ctx, float x, float y0, float y1) {
        Draw.rect(ctx, x, y0, x + 1f / Draw.guiScale(), y1, Theme.LINE);
    }

    /**
     * Darkens the blurred world behind a screen, more at the edges than in the middle, so the
     * panel reads clearly without the game disappearing behind it.
     */
    public static void backdrop(GuiGraphicsExtractor ctx, int width, int height) {
        float midY = height / 2f;
        float midX = width / 2f;
        Draw.gradientV(ctx, 0, 0, width, midY, Theme.SCRIM_EDGE, Theme.SCRIM_CENTER);
        Draw.gradientV(ctx, 0, midY, width, height, Theme.SCRIM_CENTER, Theme.SCRIM_EDGE);
        int side = Draw.withAlpha(Theme.SCRIM_EDGE, 0.5f);
        Draw.gradientH(ctx, 0, 0, midX, height, side, 0);
        Draw.gradientH(ctx, midX, 0, width, height, 0, side);
    }

    /** Opaque version of the backdrop, for screens opened with no world behind them. */
    public static void solidBackdrop(GuiGraphicsExtractor ctx, int width, int height) {
        Draw.gradient(ctx, 0, 0, width, height, Theme.BG_TOP,
                Draw.mix(Theme.BG_TOP, Theme.BG_BOTTOM, 0.5f),
                Draw.mix(Theme.BG_TOP, Theme.BG_BOTTOM, 0.5f), Theme.BG_BOTTOM);
    }

    /**
     * A progress bar. The fill runs from {@code from} to {@code to}; while not full, a soft
     * highlight sweeps along it so a long wait still looks alive.
     */
    public static void progress(GuiGraphicsExtractor ctx, float x, float y, float w, float h,
                                float fraction, int from, int to) {
        float r = h / 2f;
        Draw.roundRect(ctx, x, y, w, h, r, Theme.TRACK);
        float fw = w * Anim.clamp01(fraction);
        if (fw < 0.5f) return;
        Draw.shadow(ctx, x, y, fw, h, r, 3f, 0f, Draw.withAlpha(from, 0.35f));
        Draw.roundRectH(ctx, x, y, fw, h, r, from, to);
        if (fraction < 1f) {
            float period = 1800f;
            float t = (float) ((Anim.nowMs() % period) / period);
            float bandW = Math.max(12f, fw * 0.35f);
            float bx = x - bandW + (fw + bandW) * t;
            ctx.enableScissor(Math.round(x), Math.round(y), Math.round(x + fw), Math.round(y + h));
            Draw.gradientH(ctx, bx, y, bx + bandW / 2f, y + h, 0x00FFFFFF, 0x40FFFFFF);
            Draw.gradientH(ctx, bx + bandW / 2f, y, bx + bandW, y + h, 0x40FFFFFF, 0x00FFFFFF);
            ctx.disableScissor();
        }
    }

    /**
     * An inventory-style slot. {@code hover} (0..1) lights the edge blue with a glow; a filled slot
     * sits slightly raised.
     */
    public static void slot(GuiGraphicsExtractor ctx, float x, float y, float size, float hover, boolean filled) {
        float r = Theme.RADIUS_SLOT;
        if (hover > 0.01f) Draw.shadow(ctx, x, y, size, size, r, 4f, 0f, Draw.withAlpha(Theme.GLOW, hover));
        int base = filled ? Theme.HOVER : Theme.INSET;
        Draw.roundRectV(ctx, x, y, size, size, r,
                Draw.mix(base, 0xFF1C3566, hover * 0.8f), Draw.mix(filled ? Theme.CARD : Theme.INSET, 0xFF16284D, hover * 0.8f));
        Draw.outline(ctx, x, y, size, size, r, 1f, Draw.mix(Theme.LINE_STRONG, Theme.ACCENT, hover));
    }

    /** A small rounded label, e.g. "ACTIVE" next to a feature title. */
    public static float pill(GuiGraphicsExtractor ctx, String text, float x, float y, int textColour, int fill) {
        UiText.Style style = UiText.Style.LABEL;
        float w = UiText.width(text, style) + 12;
        float h = 12;
        Draw.roundRect(ctx, x, y, w, h, h / 2f, fill);
        UiText.draw(ctx, text, style, x + 6, UiText.centerY(style, y, h), textColour);
        return w;
    }

    /** A status dot; lit ones glow. */
    public static void dot(GuiGraphicsExtractor ctx, float cx, float cy, boolean lit, int colour) {
        if (lit) Draw.circle(ctx, cx, cy, 4f, Draw.withAlpha(colour, 0.25f));
        Draw.circle(ctx, cx, cy, 2f, lit ? colour : Theme.TEXT_3);
    }
}
