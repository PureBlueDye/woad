package com.pureblue.woad.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.function.Supplier;

/**
 * Shows a setting's current value and changes it when clicked.
 *
 * <p>The icon says what a click does: a chevron cycles through choices in place, a pencil opens
 * an editor, and a key cap captures the next key pressed.
 */
public class UiValueButton extends UiWidget {

    public enum Kind { CYCLE, EDIT, KEY }

    private final Kind kind;
    private final Supplier<String> value;
    private final Runnable action;
    private final Anim.Pulse press = new Anim.Pulse(Theme.MS_PRESS, Theme.MS_HOVER);
    private boolean listening;
    /** Dim, italic-like rendering for "not set" values. */
    private boolean muted;

    public UiValueButton(Kind kind, Supplier<String> value, Runnable action) {
        this.kind = kind;
        this.value = value;
        this.action = action;
    }

    /** A key cap waiting for a key shows a pulsing blue edge. */
    public UiValueButton listening(boolean listening) {
        this.listening = listening;
        return this;
    }

    public UiValueButton muted(boolean muted) {
        this.muted = muted;
        return this;
    }

    public int preferredWidth(int min, int max) {
        int text = UiText.width(value.get(), UiText.Style.BODY);
        return Math.max(min, Math.min(max, text + (kind == Kind.KEY ? 16 : 28)));
    }

    @Override
    protected void draw(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float hv) {
        float r = Theme.RADIUS_CONTROL;
        float p = press.value();
        String text = value.get();
        int textColour = !enabled ? Theme.TEXT_3 : (muted ? Theme.TEXT_3 : Theme.TEXT);

        if (kind == Kind.KEY) {
            if (listening) {
                float wave = (float) (0.5 + 0.5 * Math.sin(Anim.nowMs() / 180.0));
                Draw.shadow(ctx, x, y, w, h, r, 3f + 2f * wave, 0f, Draw.withAlpha(Theme.GLOW, 0.5f + 0.5f * wave));
            }
            // A key cap: raised, with a darker lip underneath.
            Draw.roundRect(ctx, x, y + 1.5f, w, h, r, 0xFF0A1122);
            int top = enabled ? Draw.mix(Theme.RAISE, 0xFF2E4270, hv) : Theme.DISABLED_FILL;
            int bottom = enabled ? Theme.HOVER : Theme.DISABLED_FILL;
            Draw.roundRectV(ctx, x, y + p, w, h, r, top, bottom);
            Draw.outline(ctx, x, y + p, w, h, r, 1f, listening ? Theme.ACCENT : Theme.LINE_STRONG);
            String shown = listening ? "Press a key…" : text;
            shown = UiText.ellipsize(shown, UiText.Style.LABEL, (int) w - 8);
            UiText.drawCentered(ctx, shown, UiText.Style.LABEL, x + w / 2f,
                    UiText.centerY(UiText.Style.LABEL, y + p, h), textColour);
            return;
        }

        int fill = enabled ? Draw.mix(Theme.INSET, Theme.INSET_HOVER, hv) : Theme.INSET;
        Draw.roundRect(ctx, x, y, w, h, r, Draw.mix(fill, Theme.HOVER, p * 0.5f));
        Draw.outline(ctx, x, y, w, h, r, 1f,
                enabled ? Draw.mix(Theme.LINE_STRONG, Draw.withAlpha(Theme.ACCENT, 0.6f), hv) : Theme.LINE);
        float iconX = x + w - 8;
        float cy = y + h / 2f;
        String shown = UiText.ellipsize(text, UiText.Style.BODY, (int) (w - 22));
        UiText.draw(ctx, shown, UiText.Style.BODY, x + 7, UiText.centerY(UiText.Style.BODY, y, h), textColour);
        int iconColour = enabled ? Draw.mix(Theme.TEXT_2, Theme.TEXT, hv) : Theme.TEXT_3;
        if (kind == Kind.CYCLE) {
            Draw.chevronDown(ctx, iconX, cy, 5f, iconColour);
        } else {
            Draw.pencil(ctx, iconX, cy, 6f, iconColour);
        }
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
