package com.pureblue.woad.core.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

/** A whole-number setting, typed in a popup and clamped to a range. */
public class IntSetting extends Setting<Integer> {

    private final int min;
    private final int max;

    public IntSetting(String name, String description, int defaultValue, int min, int max) {
        super(name, description, defaultValue);
        this.min = min;
        this.max = max;
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
