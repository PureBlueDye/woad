package com.pureblue.woad.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import java.util.function.IntSupplier;

/**
 * A {@link UiSlider} with its value written beside it, for settings rows: the number updates live
 * while the thumb is dragged.
 */
public class UiValueSlider extends UiWidget {

    /** Room kept on the right for the value. */
    private static final int VALUE_W = 24;

    private final UiSlider slider;
    private final IntSupplier value;
    private final IntFunction<String> format;
    private final Runnable onRelease;

    /**
     * @param format    how the value is written ("100", "Off"…)
     * @param onRelease called once the thumb is let go — where to save, rather than on every step
     */
    public UiValueSlider(int min, int max, int step, IntSupplier value, IntConsumer onChange,
                         IntFunction<String> format, Runnable onRelease) {
        this.slider = new UiSlider(min, max, step, value, onChange);
        this.value = value;
        this.format = format;
        this.onRelease = onRelease;
    }

    @Override
    public UiWidget bounds(float x, float y, float w, float h) {
        super.bounds(x, y, w, h);
        slider.bounds(x, y, w - VALUE_W - 4, h);
        return this;
    }

    @Override
    public UiWidget enabled(boolean enabled) {
        super.enabled(enabled);
        slider.enabled(enabled);
        return this;
    }

    @Override
    protected void draw(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float hv) {
        slider.render(ctx, mouseX, mouseY);
        String text = format.apply(value.getAsInt());
        float tx = x + w - UiText.width(text, UiText.Style.LABEL);
        UiText.draw(ctx, text, UiText.Style.LABEL, tx, UiText.centerY(UiText.Style.LABEL, y, h),
                enabled ? Theme.TEXT : Theme.TEXT_3);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        return slider.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button) {
        return slider.mouseDragged(mx, my, button);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        boolean was = slider.mouseReleased(mx, my, button);
        if (was) onRelease.run();
        return was;
    }
}
