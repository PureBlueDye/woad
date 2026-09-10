package com.pureblue.woad.core.setting;

import com.google.gson.JsonElement;

/**
 * A single configurable option belonging to a {@link com.pureblue.woad.core.Feature}.
 *
 * @param <T> the value type held by this setting
 */
public abstract class Setting<T> {

    private final String name;
    private final String description;
    protected T value;

    protected Setting(String name, String description, T defaultValue) {
        this.name = name;
        this.description = description;
        this.value = defaultValue;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public T get() {
        return value;
    }

    public void set(T value) {
        this.value = value;
    }

    /** Serializes the current value so it can be persisted to disk. */
    public abstract JsonElement write();

    /** Restores the value from a previously persisted element. */
    public abstract void read(JsonElement element);
}
