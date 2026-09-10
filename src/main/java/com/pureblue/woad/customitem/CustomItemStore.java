package com.pureblue.woad.customitem;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.pureblue.woad.core.Woad;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Remembers which item got which look.
 *
 * <p>Hypixel puts its item data at the <em>root</em> of the stack's custom data — {@code uuid} and
 * {@code id} sit directly there, not under an {@code ExtraAttributes} sub-tag as the old NBT format
 * had it. Reading one level too deep is why every item looked like a plain vanilla one.
 *
 * <p>Three levels of matching, most specific first: the per-instance {@code uuid} (only
 * <em>your</em> Hyperion), the SkyBlock {@code id} for stackables that carry no uuid, and finally
 * the plain vanilla item so the editor still works off SkyBlock. The screen says which is in play.
 */
public final class CustomItemStore {

    private static final Logger LOGGER = LoggerFactory.getLogger("Woad");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final Path FILE = FabricLoader.getInstance().getConfigDir()
            .resolve(Woad.MOD_ID).resolve("customitems.json");

    /** Prefix marking a key that matches every copy of a SkyBlock item rather than one instance. */
    public static final String BY_TYPE = "type:";
    /** Prefix for a plain vanilla item, matched by its registry id. */
    public static final String BY_VANILLA = "vanilla:";

    private static final Map<String, CustomItem> OVERRIDES = new HashMap<>();
    private static boolean loaded = false;

    private CustomItemStore() {}

    /**
     * The key identifying this stack.
     *
     * @return the instance uuid, else {@code type:<skyblock id>}, else {@code null} for a plain
     *         vanilla item that SkyBlock does not tag
     */
    public static String keyOf(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;

        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data != null) {
            CompoundTag tag = data.copyTag();
            String uuid = tag.getStringOr("uuid", "");
            if (!uuid.isBlank()) return uuid;

            String id = tag.getStringOr("id", "");
            if (!id.isBlank()) return BY_TYPE + id;
        }
        // Not a SkyBlock item: still editable, but the look then applies to every one of them.
        return BY_VANILLA + BuiltInRegistries.ITEM.getKey(stack.getItem());
    }

    /** A short, readable description of what a key matches, for the editor. */
    public static String describe(String key) {
        if (key == null) return "no item";
        if (key.startsWith(BY_TYPE)) return "every " + key.substring(BY_TYPE.length());
        if (key.startsWith(BY_VANILLA)) {
            String id = key.substring(BY_VANILLA.length());
            return "every " + id.substring(id.indexOf(':') + 1) + " (vanilla)";
        }
        return "this one only";
    }

    public static CustomItem get(String key) {
        if (key == null) return null;
        load();
        return OVERRIDES.get(key);
    }

    /** Stores an override, or drops it when it no longer changes anything. */
    public static void put(String key, CustomItem item) {
        if (key == null) return;
        load();
        if (item == null || item.isEmpty()) {
            OVERRIDES.remove(key);
        } else {
            OVERRIDES.put(key, item);
        }
        save();
    }

    public static void remove(String key) {
        put(key, null);
    }

    public static int count() {
        load();
        return OVERRIDES.size();
    }

    private static synchronized void load() {
        if (loaded) return;
        loaded = true;
        if (!Files.exists(FILE)) return;
        try (Reader reader = Files.newBufferedReader(FILE)) {
            JsonObject root = GSON.fromJson(reader, JsonObject.class);
            if (root == null) return;
            for (String key : root.keySet()) {
                if (root.get(key).isJsonObject()) {
                    OVERRIDES.put(key, CustomItem.read(root.getAsJsonObject(key)));
                }
            }
        } catch (IOException | RuntimeException e) {
            LOGGER.warn("[Woad] could not read {}", FILE, e);
        }
    }

    private static void save() {
        try {
            Files.createDirectories(FILE.getParent());
            JsonObject root = new JsonObject();
            OVERRIDES.forEach((key, item) -> root.add(key, item.write()));
            try (Writer writer = Files.newBufferedWriter(FILE)) {
                GSON.toJson(root, writer);
            }
        } catch (IOException e) {
            LOGGER.warn("[Woad] could not write {}", FILE, e);
        }
    }
}
