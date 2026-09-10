package com.pureblue.woad.core.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

/** A simple on/off setting rendered as a toggle switch in the config menu. */
public class BooleanSetting extends Setting<Boolean> {

    public BooleanSetting(String name, String description, boolean defaultValue) {
        super(name, description, defaultValue);
    }

    public boolean enabled() {
        return get();
    }

    public void toggle() {
        set(!get());
    }

    @Override
    public JsonElement write() {
        return new JsonPrimitive(get());
    }

    @Override
    public void read(JsonElement element) {
        if (element != null && element.isJsonPrimitive()) {
            set(element.getAsBoolean());
        }
    }
}
