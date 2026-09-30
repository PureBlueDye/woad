package com.pureblue.woad.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * An on/off switch — what the interface uses instead of checkboxes.
 *
 * <p>The state is read from and written to the caller's own setting on every frame, so the
 * switch never holds a copy that could drift. The knob slides with a slight spring, the track
 * fills with a blue gradient and glows while on.
 */
public class UiToggle extends UiWidget {

    private final BooleanSupplier value;
    private final Consumer<Boolean> onChange;
    private final Anim.Toggle knob = new Anim.Toggle(Theme.MS_TOGGLE, Anim.Ease.OUT_BACK);

    public UiToggle(BooleanSupplier value, Consumer<Boolean> onChange) {
        this.value = value;
        this.onChange = onChange;
        this.w = Theme.SWITCH_W;
        this.h = Theme.SWITCH_H;
    }

    /** Places the switch with its standard size. */
    public UiToggle at(float x, float y) {
        bounds(x, y, Theme.SWITCH_W, Theme.SWITCH_H);
        return this;
    }

    @Override
    protected void draw(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float hv) {
        boolean on = value.getAsBoolean();
        float k = knob.update(on);
        float r = h / 2f;
        float kVisual = Anim.clamp01(k);

        if (!enabled) {
            Draw.roundRect(ctx, x, y, w, h, r, Theme.SWITCH_DISABLED);
            Draw.outline(ctx, x, y, w, h, r, 1f, Theme.LINE);
        } else {
            if (kVisual > 0.01f) {
                Draw.shadow(ctx, x, y, w, h, r, 4f, 0f, Draw.withAlpha(Theme.GLOW, 0.8f * kVisual));
            }
            Draw.roundRect(ctx, x, y, w, h, r, Draw.mix(Theme.SWITCH_OFF, Theme.RAISE, hv * (1 - kVisual)));
            if (kVisual > 0.01f) {
                Draw.roundRectH(ctx, x, y, w, h, r,
                        Draw.withAlpha(Theme.FILL, kVisual), Draw.withAlpha(Theme.ACCENT, kVisual));
            }
            Draw.outline(ctx, x, y, w, h, r, 1f, Draw.withAlpha(Theme.LINE_STRONG, 1 - kVisual));
        }

        float knobR = r - 1.5f;
        float left = x + 1.5f + knobR;
        float right = x + w - 1.5f - knobR;
        float kx = left + (right - left) * k;
        float ky = y + h / 2f;
        if (enabled) Draw.shadow(ctx, kx - knobR, ky - knobR, knobR * 2, knobR * 2, knobR, 2f, 0.6f, Theme.SHADOW);
        Draw.circle(ctx, kx, ky, knobR, enabled ? Theme.KNOB : Theme.TEXT_3);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (!enabled || button != 0 || !contains(mx, my)) return false;
        onChange.accept(!value.getAsBoolean());
        clickSound();
        return true;
    }
}
