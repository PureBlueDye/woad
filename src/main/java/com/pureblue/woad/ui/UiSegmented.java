package com.pureblue.woad.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.List;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/**
 * A row of mutually exclusive options — tabs, or a two-way choice such as Lava / Water.
 *
 * <p>A raised pill marks the selected option and slides to a new one instead of jumping.
 */
public class UiSegmented extends UiWidget {

    private static final float INNER = 2f;
    private static final float OPTION_PAD = 9f;

    private final List<String> options;
    private final IntSupplier selected;
    private final IntConsumer onSelect;
    private final Anim.Tween pillX = new Anim.Tween(Theme.MS_SLIDE, Anim.Ease.OUT_CUBIC);
    private final Anim.Tween pillW = new Anim.Tween(Theme.MS_SLIDE, Anim.Ease.OUT_CUBIC);
    private float[] starts = new float[0];
    private float[] widths = new float[0];

    public UiSegmented(List<String> options, IntSupplier selected, IntConsumer onSelect) {
        this.options = options;
        this.selected = selected;
        this.onSelect = onSelect;
    }

    /** Width that fits every option at its natural size. */
    public int preferredWidth() {
        float total = INNER * 2;
        for (String option : options) {
            total += UiText.width(option, UiText.Style.LABEL) + OPTION_PAD * 2;
        }
        return (int) Math.ceil(total);
    }

    /**
     * Shares the width among the options. Spare room widens them all equally; when room is short
     * the padding around each label shrinks (to a minimum) instead of the row spilling out.
     */
    private void layout() {
        starts = new float[options.size()];
        widths = new float[options.size()];
        float available = w - INNER * 2;
        float text = 0f;
        for (String option : options) text += UiText.width(option, UiText.Style.LABEL);
        float pad = Math.max(3f, Math.min(OPTION_PAD, (available - text) / (options.size() * 2f)));
        float extra = Math.max(0f, available - text - pad * 2 * options.size()) / options.size();
        float cursor = x + INNER;
        for (int i = 0; i < options.size(); i++) {
            float optionW = UiText.width(options.get(i), UiText.Style.LABEL) + pad * 2 + extra;
            starts[i] = cursor;
            widths[i] = optionW;
            cursor += optionW;
        }
    }

    @Override
    protected void draw(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float hv) {
        layout();
        float r = Theme.RADIUS_CONTROL + 1f;
        Draw.roundRect(ctx, x, y, w, h, r, Theme.INSET);
        Draw.outline(ctx, x, y, w, h, r, 1f, Theme.LINE);

        int current = Math.max(0, Math.min(options.size() - 1, selected.getAsInt()));
        pillX.set(starts[current]);
        pillW.set(widths[current]);
        float px = pillX.get();
        float pw = pillW.get();
        float pr = r - INNER / 2f;
        Draw.roundRectV(ctx, px, y + INNER, pw, h - INNER * 2, pr, Theme.RAISE, Theme.HOVER);
        Draw.outline(ctx, px, y + INNER, pw, h - INNER * 2, pr, 1f, Theme.LINE_STRONG);

        for (int i = 0; i < options.size(); i++) {
            boolean over = enabled && mouseX >= starts[i] && mouseX < starts[i] + widths[i]
                    && mouseY >= y && mouseY < y + h;
            int colour = !enabled ? Theme.TEXT_3
                    : i == current ? Theme.TEXT : (over ? Theme.TEXT : Theme.TEXT_2);
            String label = options.get(i);
            float lw = UiText.width(label, UiText.Style.LABEL);
            UiText.draw(ctx, label, UiText.Style.LABEL, starts[i] + (widths[i] - lw) / 2f,
                    UiText.centerY(UiText.Style.LABEL, y, h), colour);
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (!enabled || button != 0 || !contains(mx, my)) return false;
        for (int i = 0; i < starts.length; i++) {
            if (mx >= starts[i] && mx < starts[i] + widths[i]) {
                if (i != selected.getAsInt()) {
                    onSelect.accept(i);
                    clickSound();
                }
                return true;
            }
        }
        return false;
    }
}
