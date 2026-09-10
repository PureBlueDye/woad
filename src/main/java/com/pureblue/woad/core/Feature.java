package com.pureblue.woad.core;

import com.pureblue.woad.core.setting.Setting;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Base class for every mod feature shown in the config menu.
 *
 * <p>A feature has a stable {@code id} (used for persistence), a display {@code name} and
 * {@code description}, a master enabled flag, and a list of {@link Setting}s. Subclasses add their
 * own settings via {@link #addSetting(Setting)} and react to chat / ticks through the optional
 * hooks below.
 */
public abstract class Feature {

    private final String id;
    private final String name;
    private final String description;
    private boolean enabled;
    private final List<Setting<?>> settings = new ArrayList<>();

    protected Feature(String id, String name, String description, boolean enabledByDefault) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.enabled = enabledByDefault;
    }

    protected <T extends Setting<?>> T addSetting(T setting) {
        settings.add(setting);
        return setting;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        if (this.enabled == enabled) return;
        this.enabled = enabled;
        if (enabled) onEnabled();
        else onDisabled();
    }

    public List<Setting<?>> getSettings() {
        return settings;
    }

    // ---- Optional hooks -------------------------------------------------------------------

    /** Called when the feature is switched on. */
    protected void onEnabled() {}

    /** Called when the feature is switched off. */
    protected void onDisabled() {}

    /** Called every client tick while the feature is enabled. */
    public void onClientTick() {}

    /**
     * Called for every received game chat message (stripped of formatting) while enabled.
     *
     * @param message the plain-text message content
     */
    public void onChatMessage(String message) {}

    /**
     * Called for player chat (singleplayer and vanilla servers), where the sender and the text
     * arrive already separated — no parsing needed. Hypixel's chat is system chat and arrives
     * through {@link #onChatMessage} instead.
     *
     * @param sender  the player's name
     * @param content what they typed
     */
    public void onPlayerChatMessage(String sender, String content) {}

    /** Whether this feature shows an on/off master switch in the menu (false = no toggle). */
    public boolean hasToggle() {
        return true;
    }

    /** Whether this feature draws its own UI directly in the menu's content panel. */
    public boolean hasCustomPanel() {
        return false;
    }

    /** Draws this feature's custom panel content within the given rect. */
    public void renderPanel(net.minecraft.client.gui.GuiGraphicsExtractor ctx, net.minecraft.client.gui.screens.Screen parent,
                            int x, int top, int right, int bottom, int mouseX, int mouseY) {}

    /** Handles a click inside this feature's custom panel; returns true if consumed. */
    public boolean panelMouseClicked(net.minecraft.client.gui.screens.Screen parent, double mouseX, double mouseY, int button) {
        return false;
    }

    /** Called when the menu screen is removed, so custom panels can free resources (e.g. textures). */
    public void panelRemoved() {}

    /** True if this feature offers a separate config screen, opened from the menu via an "Open" button. */
    public boolean hasConfigScreen() {
        return false;
    }

    /** Builds this feature's config screen, returning to {@code parent} when closed. */
    public net.minecraft.client.gui.screens.Screen createConfigScreen(net.minecraft.client.gui.screens.Screen parent) {
        return null;
    }

    /** Draws extra controls in this feature's menu panel, below the description / Open button. */
    public void renderExtra(net.minecraft.client.gui.GuiGraphicsExtractor ctx, net.minecraft.client.gui.screens.Screen parent,
                            int left, int y, int right, int mouseX, int mouseY) {}

    /**
     * Height in pixels taken by {@link #renderExtra}, so the menu can lay the settings out below it.
     * Must match what the feature actually draws — 0 means it draws nothing.
     */
    public int extraHeight() {
        return 0;
    }

    /** Handles a click on the extra controls; returns true if consumed. */
    public boolean extraMouseClicked(net.minecraft.client.gui.screens.Screen parent, double mouseX, double mouseY, int button) {
        return false;
    }

    /** Optional: write extra per-feature state (beyond {@link Setting}s) into its config node. */
    public void writeConfig(com.google.gson.JsonObject node) {}

    /** Optional: restore the extra state written by {@link #writeConfig}. */
    public void readConfig(com.google.gson.JsonObject node) {}

    /** Convenience: a colored "[Woad]"-prefixed chat line. */
    protected void sendModMessage(Component body) {
        Woad.sendPrefixedMessage(body);
    }
}
