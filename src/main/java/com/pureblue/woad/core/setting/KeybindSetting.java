package com.pureblue.woad.core.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;

/** A rebindable key (GLFW key code), shown as a row with a "listen for key" button in the menu. */
public class KeybindSetting extends Setting<Integer> {

    public KeybindSetting(String name, String description) {
        this(name, description, GLFW.GLFW_KEY_UNKNOWN); // -1 = unbound
    }

    public KeybindSetting(String name, String description, int defaultKey) {
        super(name, description, defaultKey);
    }

    public int getKey() {
        return get();
    }

    public void setKey(int key) {
        set(key);
    }

    public boolean matches(int key) {
        return key != GLFW.GLFW_KEY_UNKNOWN && get() == key;
    }

    /** Whether the key is currently held down. */
    public boolean isHeld() {
        int key = get();
        Minecraft mc = Minecraft.getInstance();
        return key != GLFW.GLFW_KEY_UNKNOWN && mc.getWindow() != null
                && InputConstants.isKeyDown(mc.getWindow(), key);
    }

    /** Human-readable key name (e.g. "1", "F", "Left Alt", "None"). */
    public String keyName() {
        int key = get();
        if (key == GLFW.GLFW_KEY_UNKNOWN) return "None";
        String name = GLFW.glfwGetKeyName(key, 0);
        if (name != null && !name.isBlank()) return name.toUpperCase(Locale.ROOT);
        // Keys with no printable name (Alt, Ctrl, F5, ...) still have a translated label.
        String label = InputConstants.Type.KEYSYM.getOrCreate(key).getDisplayName().getString();
        return label.isBlank() ? "Key " + key : label;
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
