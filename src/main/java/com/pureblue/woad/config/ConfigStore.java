package com.pureblue.woad.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.pureblue.woad.core.Woad;
import com.pureblue.woad.core.Feature;
import com.pureblue.woad.core.FeatureManager;
import com.pureblue.woad.core.setting.Setting;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/** Saves and restores every feature's enabled flag and settings to {@code config/woad.json}. */
public final class ConfigStore {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH =
            FabricLoader.getInstance().getConfigDir().resolve(Woad.MOD_ID + ".json");

    /** Where the mod's settings lived before it was renamed to Woad. */
    private static final Path LEGACY_PATH =
            FabricLoader.getInstance().getConfigDir().resolve("dyeaddon.json");
    private static final Path LEGACY_DIR =
            FabricLoader.getInstance().getConfigDir().resolve("dyeaddon");

    private ConfigStore() {}

    /**
     * Takes over the settings written under the mod's former name, once.
     *
     * <p>Renaming the mod changes where its config lives, which would otherwise silently reset every
     * feature — API key, prompts, inventory buttons and HUD position included. The old files are
     * left in place rather than moved, so nothing is lost if this goes wrong.
     */
    private static void adoptLegacyConfig() {
        try {
            if (!Files.exists(PATH) && Files.exists(LEGACY_PATH)) {
                Files.copy(LEGACY_PATH, PATH);
            }
            Path dir = FabricLoader.getInstance().getConfigDir().resolve(Woad.MOD_ID);
            if (!Files.exists(dir) && Files.isDirectory(LEGACY_DIR)) {
                Files.createDirectories(dir);
                try (var files = Files.list(LEGACY_DIR)) {
                    for (Path file : files.toList()) {
                        Files.copy(file, dir.resolve(file.getFileName()));
                    }
                }
            }
        } catch (IOException ignored) {
            // A fresh install is a perfectly good outcome; never block startup over this.
        }
    }

    public static void load() {
        adoptLegacyConfig();
        if (!Files.exists(PATH)) return;
        try (Reader reader = Files.newBufferedReader(PATH)) {
            JsonObject root = GSON.fromJson(reader, JsonObject.class);
            if (root == null) return;

            for (Feature feature : FeatureManager.getFeatures()) {
                if (!root.has(feature.getId()) || !root.get(feature.getId()).isJsonObject()) continue;
                JsonObject node = root.getAsJsonObject(feature.getId());

                if (node.has("enabled")) {
                    feature.setEnabled(node.get("enabled").getAsBoolean());
                }
                if (node.has("settings") && node.get("settings").isJsonObject()) {
                    JsonObject settings = node.getAsJsonObject("settings");
                    for (Setting<?> setting : feature.getSettings()) {
                        if (settings.has(setting.getName())) {
                            setting.read(settings.get(setting.getName()));
                        }
                    }
                }
                feature.readConfig(node);
            }
        } catch (Exception e) {
            System.err.println("[Woad] Failed to load config: " + e.getMessage());
        }
    }

    public static void save() {
        JsonObject root = new JsonObject();
        for (Feature feature : FeatureManager.getFeatures()) {
            JsonObject node = new JsonObject();
            node.addProperty("enabled", feature.isEnabled());

            JsonObject settings = new JsonObject();
            for (Setting<?> setting : feature.getSettings()) {
                settings.add(setting.getName(), setting.write());
            }
            node.add("settings", settings);
            feature.writeConfig(node);
            root.add(feature.getId(), node);
        }

        try {
            Files.createDirectories(PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(PATH)) {
                GSON.toJson(root, writer);
            }
        } catch (IOException e) {
            System.err.println("[Woad] Failed to save config: " + e.getMessage());
        }
    }
}
