package com.pureblue.woad.core.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import java.util.List;

/**
 * A setting that cycles through a list of options when clicked.
 *
 * <p>The list is fixed unless a subclass overrides {@link #getOptions()} — the AI prompt picker
 * does, to offer whatever files are in the prompt folder right now.
 */
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
        List<String> options = getOptions();
        if (options.isEmpty()) return;
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
        if (element != null && element.isJsonPrimitive() && getOptions().contains(element.getAsString())) {
            set(element.getAsString());
        }
    }
}
