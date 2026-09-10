package com.pureblue.woad.core.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import java.util.List;

/** A setting that cycles through a fixed list of options when clicked. */
public class ModeSetting extends Setting<String> {

    private final List<String> options;

    public ModeSetting(String name, String description, String defaultValue, List<String> options) {
        super(name, description, defaultValue);
        this.options = options;
    }

    public List<String> getOptions() {
        return options;
    }

    /** Advances to the next option, wrapping around. */
    public void cycle() {
        int index = options.indexOf(get());
        set(options.get((index + 1) % options.size()));
    }

    public boolean is(String option) {
        return option.equals(get());
    }

    @Override
    public JsonElement write() {
        return new JsonPrimitive(get());
    }

    @Override
    public void read(JsonElement element) {
        if (element != null && element.isJsonPrimitive() && options.contains(element.getAsString())) {
            set(element.getAsString());
        }
    }
}
