package com.pureblue.woad.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/**
 * A horizontal slider for a whole number in a range, snapped to a step.
 *
 * <p>Click anywhere on the track to jump there, drag to adjust. The filled part of the track is a
 * blue-to-cyan gradient; the thumb's halo grows on hover and while dragging.
 */
public class UiSlider extends UiWidget {

    private final int min;
    private final int max;
    private final int step;
    private final IntSupplier value;
    private final IntConsumer onChange;
    private final Anim.Toggle grab = new Anim.Toggle(Theme.MS_HOVER, Anim.Ease.OUT_CUBIC);
    private boolean dragging;

    public UiSlider(int min, int max, int step, IntSupplier value, IntConsumer onChange) {
        this.min = min;
        this.max = Math.max(min + 1, max);
        this.step = Math.max(1, step);
        this.value = value;
        this.onChange = onChange;
    }

    private float fraction() {
        return Anim.clamp01((value.getAsInt() - min) / (float) (max - min));
    }

    @Override
    protected void draw(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float hv) {
        float g = grab.update(dragging);
        float trackH = 3f;
        float thumbR = 4.5f;
        float left = x + thumbR;
        float right = x + w - thumbR;
        float cy = y + h / 2f;
        float tx = left + (right - left) * fraction();

        Draw.roundRect(ctx, left, cy - trackH / 2f, right - left, trackH, trackH / 2f,
                enabled ? Theme.TRACK : Theme.SWITCH_DISABLED);
        if (enabled && tx > left) {
            Draw.roundRectH(ctx, left, cy - trackH / 2f, tx - left, trackH, trackH / 2f, Theme.FILL, Theme.CYAN);
        }
        if (enabled) {
            float halo = 2f + 2f * Math.max(hv, g);
            Draw.circle(ctx, tx, cy, thumbR + halo, Draw.withAlpha(Theme.GLOW_SOFT, 0.6f + 0.4f * Math.max(hv, g)));
            Draw.shadow(ctx, tx - thumbR, cy - thumbR, thumbR * 2, thumbR * 2, thumbR, 2f, 0.6f, Theme.SHADOW);
        }
        Draw.circle(ctx, tx, cy, thumbR, enabled ? Theme.KNOB : Theme.TEXT_3);
    }

    private void setFrom(double mx) {
        float thumbR = 4.5f;
        float t = Anim.clamp01((float) ((mx - (x + thumbR)) / (w - thumbR * 2)));
        int raw = Math.round(min + t * (max - min));
        int snapped = min + Math.round((raw - min) / (float) step) * step;
        snapped = Math.max(min, Math.min(max, snapped));
        if (snapped != value.getAsInt()) onChange.accept(snapped);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (!enabled || button != 0 || !contains(mx, my)) return false;
        dragging = true;
        setFrom(mx);
        return true;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button) {
        if (!dragging) return false;
        setFrom(mx);
        return true;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        boolean was = dragging;
        dragging = false;
        return was;
    }
}
