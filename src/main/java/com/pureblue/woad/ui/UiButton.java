package com.pureblue.woad.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * A button: primary (filled blue), secondary (raised) or ghost (text only until hovered).
 *
 * <p>Hover brightens it and, for primary buttons, strengthens the glow beneath; a click presses it
 * in (darker, 2% smaller) and springs back; a disabled button is flat grey with no glow.
 */
public class UiButton extends UiWidget {

    public enum Variant { PRIMARY, SECONDARY, GHOST }

    private String label;
    private final Variant variant;
    private final Runnable action;
    private final Anim.Pulse press = new Anim.Pulse(Theme.MS_PRESS, Theme.MS_HOVER);
    /** Colour shown as a small swatch before the label, or -1 for none. */
    private int swatch = -1;

    public UiButton(String label, Variant variant, Runnable action) {
        this.label = label;
        this.variant = variant;
        this.action = action;
    }

    public UiButton label(String label) {
        this.label = label;
        return this;
    }

    public String label() {
        return label;
    }

    public UiButton swatch(int rgb) {
        this.swatch = rgb;
        return this;
    }

    /** Width that fits the label with comfortable padding. */
    public int preferredWidth() {
        int text = UiText.width(label, UiText.Style.LABEL);
        return text + 20 + (swatch >= 0 ? 12 : 0);
    }

    @Override
    protected void draw(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float hv) {
        float p = press.value();
        float cx = x + w / 2f;
        float cy = y + h / 2f;
        float scale = 1f - 0.02f * p;
        ctx.pose().pushMatrix();
        ctx.pose().translate(cx, cy);
        ctx.pose().scale(scale, scale);
        ctx.pose().translate(-cx, -cy);

        float r = Theme.RADIUS_CONTROL;
        int textColour;
        if (!enabled) {
            Draw.roundRect(ctx, x, y, w, h, r, variant == Variant.GHOST ? 0 : Theme.DISABLED_FILL);
            if (variant != Variant.GHOST) Draw.outline(ctx, x, y, w, h, r, 1f, Theme.LINE);
            textColour = Theme.TEXT_3;
        } else if (variant == Variant.PRIMARY) {
            if (hv > 0.01f) Draw.shadow(ctx, x, y, w, h, r, 5f, 0f, Draw.withAlpha(Theme.GLOW, hv));
            int top = Draw.mix(Draw.mix(Theme.FILL, Theme.FILL_HOVER, hv), Theme.FILL_PRESS, p);
            int bottom = Draw.mix(Draw.mix(Theme.FILL_PRESS, Theme.FILL, hv), Theme.FILL_DEEP, p);
            Draw.roundRectV(ctx, x, y, w, h, r, top, bottom);
            Draw.outline(ctx, x, y, w, h, r, 1f, 0x1FFFFFFF, 0x00FFFFFF);
            textColour = Theme.TEXT_ON_FILL;
        } else if (variant == Variant.SECONDARY) {
            int fill = Draw.mix(Draw.mix(Theme.HOVER, Theme.RAISE, hv), Theme.INSET, p);
            Draw.roundRect(ctx, x, y, w, h, r, fill);
            Draw.outline(ctx, x, y, w, h, r, 1f, Draw.mix(Theme.LINE_STRONG, Theme.LINE_HOVER, hv), Theme.LINE_STRONG);
            textColour = Theme.TEXT;
        } else {
            if (hv > 0.01f) Draw.roundRect(ctx, x, y, w, h, r, Draw.withAlpha(Theme.GLOW_SOFT, hv));
            textColour = Draw.mix(Theme.TEXT_2, Theme.TEXT, hv);
        }

        UiText.Style style = UiText.Style.LABEL;
        String shown = UiText.ellipsize(label, style, (int) w - 12 - (swatch >= 0 ? 12 : 0));
        int textW = UiText.width(shown, style);
        float contentW = textW + (swatch >= 0 ? 12 : 0);
        float tx = x + (w - contentW) / 2f;
        if (swatch >= 0) {
            Draw.roundRect(ctx, tx, cy - 3.5f, 7, 7, 2f, 0xFF000000 | swatch);
            Draw.outline(ctx, tx, cy - 3.5f, 7, 7, 2f, 1f, 0x33FFFFFF);
            tx += 12;
        }
        UiText.draw(ctx, shown, style, tx, UiText.centerY(style, y, h), textColour);

        ctx.pose().popMatrix();
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (!enabled || button != 0 || !contains(mx, my)) return false;
        press.fire();
        clickSound();
        action.run();
        return true;
    }
}
