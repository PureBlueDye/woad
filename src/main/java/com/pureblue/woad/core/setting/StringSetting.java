package com.pureblue.woad.core.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

/** A free-text setting, edited through a small popup. Can be masked (API keys). */
public class StringSetting extends Setting<String> {

    private final boolean secret;

    public StringSetting(String name, String description, String defaultValue) {
        this(name, description, defaultValue, false);
    }

    public StringSetting(String name, String description, String defaultValue, boolean secret) {
        super(name, description, defaultValue == null ? "" : defaultValue);
        this.secret = secret;
    }

    public boolean isSecret() {
        return secret;
    }

    /** What the menu shows: secrets are masked, empty values read "<not set>". */
    public String display() {
        String value = get();
        if (value == null || value.isBlank()) return "<not set>";
        if (!secret) return value;
        return value.length() <= 4 ? "****" : "****" + value.substring(value.length() - 4);
    }

    @Override
    public JsonElement write() {
        return new JsonPrimitive(get());
    }

    @Override
    public void read(JsonElement element) {
        if (element != null && element.isJsonPrimitive()) {
            set(element.getAsString());
        }
    }
}
