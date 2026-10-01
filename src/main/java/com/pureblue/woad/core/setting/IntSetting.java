package com.pureblue.woad.core.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import java.util.function.IntFunction;

/**
 * A whole-number setting, clamped to a range. Typed in a popup, or dragged on a slider when the
 * setting asks for one with {@link #slider}.
 */
public class IntSetting extends Setting<Integer> {

    private final int min;
    private final int max;
    /** Slider step, or 0 to type the value instead. */
    private int sliderStep;
    private IntFunction<String> sliderLabel = Integer::toString;

    public IntSetting(String name, String description, int defaultValue, int min, int max) {
        super(name, description, defaultValue);
        this.min = min;
        this.max = max;
    }

    /** Shows the setting as a slider moving by {@code step}, its value written as {@code label} says. */
    public IntSetting slider(int step, IntFunction<String> label) {
        this.sliderStep = Math.max(1, step);
        this.sliderLabel = label;
        return this;
    }

    public boolean isSlider() {
        return sliderStep > 0;
    }

    public int sliderStep() {
        return sliderStep;
    }

    public String sliderLabel(int value) {
        return sliderLabel.apply(value);
    }

    public int min() {
        return min;
    }

    public int max() {
        return max;
    }

    @Override
    public void set(Integer value) {
        super.set(Math.max(min, Math.min(max, value)));
    }

    /** Parses typed text, keeping the current value if it isn't a number. */
    public void parse(String text) {
        try {
            set(Integer.parseInt(text.trim()));
        } catch (NumberFormatException ignored) {
            // keep the current value
        }
    }

    @Override
    public JsonElement write() {
        return new JsonPrimitive(get());
    }

    @Override
    public void read(JsonElement element) {
        if (element != null && element.isJsonPrimitive()) {
            set(element.getAsInt());
        }
    }
}
