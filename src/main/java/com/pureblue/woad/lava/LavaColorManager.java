package com.pureblue.woad.lava;

import net.fabricmc.loader.api.FabricLoader;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.resources.Identifier;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Handles the lava colour filter (ported from the standalone CustomLava mod into Woad).
 *
 * <p>The mixin hands us the live NativeImage of the lava AND water sprites. The filter only changes
 * the LAVA sprites; the water sprites are kept purely as a pixel source. A "master" switch (driven
 * by the Woad {@code Lava Filter} feature toggle) gates the whole effect: when off, lava renders
 * vanilla. Colour, the water-texture toggle and the custom palette are saved in
 * {@code config/lavafilter.properties}.
 */
public final class LavaColorManager {

    public static final LavaColorManager INSTANCE = new LavaColorManager();
    public static final int DEFAULT_COLOR = 0xFF8000;
    public static final int CUSTOM_SLOTS = 10;
    public static final int EMPTY = -1;
    /** Vanilla default water tint (so water-textured lava actually looks like water). */
    public static final int WATER_TINT = 0x3F76E4;

    private static final Identifier LAVA_STILL  = Identifier.fromNamespaceAndPath("minecraft", "block/lava_still");
    private static final Identifier LAVA_FLOW   = Identifier.fromNamespaceAndPath("minecraft", "block/lava_flow");
    private static final Identifier WATER_STILL = Identifier.fromNamespaceAndPath("minecraft", "block/water_still");
    private static final Identifier WATER_FLOW  = Identifier.fromNamespaceAndPath("minecraft", "block/water_flow");

    private static final Path CONFIG =
        FabricLoader.getInstance().getConfigDir().resolve("lavafilter.properties");

    private static final class Captured {
        final NativeImage image;
        final int[] pristine; // ARGB
        final int width;
        final int height;

        Captured(NativeImage image) {
            this.image = image;
            this.width = image.getWidth();
            this.height = image.getHeight();
            this.pristine = new int[width * height];
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    this.pristine[y * width + x] = image.getPixel(x, y);
                }
            }
        }
    }

    private final Map<Identifier, Captured> captured = new HashMap<>();
    private boolean enabled = false;
    private int color = DEFAULT_COLOR; // 0xRRGGBB
    private boolean useWaterTexture = false;   // pending UI choice (button + preview)
    private boolean appliedWaterTexture = false; // committed state used for rendering
    private final int[] customSlots = new int[CUSTOM_SLOTS];

    private LavaColorManager() {
        Arrays.fill(customSlots, EMPTY);
    }

    // --- colorize -------------------------------------------------------------

    static int colorize(int src, int color) {
        int a = (src >>> 24) & 0xFF;
        int r = (src >> 16) & 0xFF;
        int g = (src >> 8) & 0xFF;
        int b = src & 0xFF;
        int v = Math.max(r, Math.max(g, b));
        int tr = (color >> 16) & 0xFF;
        int tg = (color >> 8) & 0xFF;
        int tb = color & 0xFF;
        return (a << 24)
            | ((tr * v / 255) << 16)
            | ((tg * v / 255) << 8)
            | (tb * v / 255);
    }

    // --- toggles --------------------------------------------------------------

    /** Pending choice shown by the button and the preview. */
    public boolean isUseWaterTexture() {
        return useWaterTexture;
    }

    /** Committed state actually used for rendering (only changes on Apply). */
    public boolean isWaterTextureApplied() {
        return appliedWaterTexture;
    }

    public void setUseWaterTexture(boolean b) {
        this.useWaterTexture = b; // pending only — not applied until commitTexture()
        save();
    }

    /** Commit the pending texture choice; call right before reloading resources (Apply). */
    public void commitTexture() {
        this.appliedWaterTexture = this.useWaterTexture;
        rebuild();
    }

    private boolean isTracked(Identifier id) {
        return id.equals(LAVA_STILL) || id.equals(LAVA_FLOW)
            || id.equals(WATER_STILL) || id.equals(WATER_FLOW);
    }

    private Identifier sourceStill() {
        return useWaterTexture ? WATER_STILL : LAVA_STILL;
    }

    // --- config ---------------------------------------------------------------

    public void load() {
        try {
            if (Files.exists(CONFIG)) {
                Properties p = new Properties();
                try (Reader r = Files.newBufferedReader(CONFIG)) {
                    p.load(r);
                }
                this.enabled = Boolean.parseBoolean(p.getProperty("enabled", "false"));
                this.color = parseHexOr(p.getProperty("color"), DEFAULT_COLOR);
                this.useWaterTexture = Boolean.parseBoolean(p.getProperty("watertexture", "false"));
                this.appliedWaterTexture = this.useWaterTexture; // saved state is active at startup
                for (int i = 0; i < CUSTOM_SLOTS; i++) {
                    this.customSlots[i] = parseHexOr(p.getProperty("custom" + i), EMPTY);
                }
            }
        } catch (Exception e) {
            // unreadable config -> defaults
        }
    }

    private void save() {
        try {
            Properties p = new Properties();
            p.setProperty("enabled", Boolean.toString(enabled));
            p.setProperty("color", String.format("%06X", color));
            p.setProperty("watertexture", Boolean.toString(useWaterTexture));
            for (int i = 0; i < CUSTOM_SLOTS; i++) {
                p.setProperty("custom" + i, customSlots[i] == EMPTY
                    ? "none" : String.format("%06X", customSlots[i]));
            }
            Files.createDirectories(CONFIG.getParent());
            try (Writer w = Files.newBufferedWriter(CONFIG)) {
                p.store(w, "Lava Filter - saved palette");
            }
        } catch (Exception e) {
            // write failure -> ignore
        }
    }

    private static int parseHexOr(String s, int def) {
        if (s == null) {
            return def;
        }
        s = s.trim();
        if (s.isEmpty() || s.equalsIgnoreCase("none")) {
            return def;
        }
        if (s.startsWith("#")) {
            s = s.substring(1);
        }
        try {
            return (int) (Long.parseLong(s, 16) & 0xFFFFFF);
        } catch (NumberFormatException e) {
            return def;
        }
    }

    // --- state ----------------------------------------------------------------

    public boolean isEnabled() {
        return enabled;
    }

    public int getColor() {
        return color;
    }

    public int getCustomSlot(int i) {
        return (i >= 0 && i < CUSTOM_SLOTS) ? customSlots[i] : EMPTY;
    }

    public void setCustomSlot(int i, int rgb) {
        if (i >= 0 && i < CUSTOM_SLOTS) {
            customSlots[i] = rgb & 0xFFFFFF;
            save();
        }
    }

    public void clearCustomSlot(int i) {
        if (i >= 0 && i < CUSTOM_SLOTS) {
            customSlots[i] = EMPTY;
            save();
        }
    }

    /** Called by the mixin for every sprite created. Only keeps lava/water. */
    public void tryCapture(Identifier id, NativeImage image) {
        if (!isTracked(id)) {
            return;
        }
        captured.put(id, new Captured(image));
        // Rebuild whenever a relevant sprite arrives (whichever is last makes it correct).
        rebuild();
    }

    /** Turns the filter on with the given colour (0xRRGGBB). Visible after "Apply". */
    public void setColor(int rgb) {
        this.color = rgb & 0xFFFFFF;
        this.enabled = true;
        rebuild();
        save();
    }

    /** Clears the colour filter only. Keeps the Lava/Water texture choice as-is
     *  (water with no colour falls back to the default biome water tint). */
    public void reset() {
        this.enabled = false;
        rebuild();
        save();
    }

    /**
     * Applies the colour filter to the lava textures. In water-texture mode the lava texture
     * is left original (the FluidRenderHandler swaps lava to water sprites at render time, so
     * the lava texture itself isn't drawn).
     */
    private void rebuild() {
        Captured lavaStill = captured.get(LAVA_STILL);
        Captured lavaFlow  = captured.get(LAVA_FLOW);
        if (lavaStill != null) {
            applyLava(lavaStill);
        }
        if (lavaFlow != null) {
            applyLava(lavaFlow);
        }
        rebuildWaterFlow();
    }

    private void applyLava(Captured c) {
        boolean colour = enabled && !appliedWaterTexture;
        for (int i = 0; i < c.pristine.length; i++) {
            int p = c.pristine[i];
            c.image.setPixel(i % c.width, i / c.width, colour ? colorize(p, color) : p);
        }
    }

    /**
     * In water-texture mode, brighten the water flow sprite so it matches the (brighter) still
     * sprite. The flow texture is grayscale, so scaling its luminance keeps the hue once the
     * blue tint is applied at render time, and the flow pattern is preserved. Restored when off.
     */
    private void rebuildWaterFlow() {
        Captured wf = captured.get(WATER_FLOW);
        Captured ws = captured.get(WATER_STILL);
        if (wf == null) {
            return;
        }
        if (isWaterTextureApplied() && ws != null) {
            float factor = avgLuma(ws) / Math.max(1f, avgLuma(wf));
            for (int i = 0; i < wf.pristine.length; i++) {
                wf.image.setPixel(i % wf.width, i / wf.width, boost(wf.pristine[i], factor));
            }
        } else {
            for (int i = 0; i < wf.pristine.length; i++) {
                wf.image.setPixel(i % wf.width, i / wf.width, wf.pristine[i]);
            }
        }
    }

    private static float avgLuma(Captured c) {
        long sum = 0;
        int count = 0;
        for (int p : c.pristine) {
            if (((p >>> 24) & 0xFF) < 8) {
                continue; // ignore transparent pixels
            }
            sum += Math.max((p >> 16) & 0xFF, Math.max((p >> 8) & 0xFF, p & 0xFF));
            count++;
        }
        return count == 0 ? 1f : (float) sum / count;
    }

    /** Scales RGB up by {@code factor}, but clamps the per-pixel factor so the brightest
     *  channel hits at most 255 — brightens while preserving hue/saturation, never clipping. */
    private static int boost(int argb, float factor) {
        int a = (argb >>> 24) & 0xFF;
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;
        int m = Math.max(r, Math.max(g, b));
        float ef = m <= 0 ? factor : Math.min(factor, 255f / m);
        r = Math.round(r * ef);
        g = Math.round(g * ef);
        b = Math.round(b * ef);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    // --- preview (what lava will look like) -----------------------------------

    public int getPreviewSize() {
        Captured c = captured.get(sourceStill());
        return c == null ? 0 : c.width;
    }

    public int getPreviewFrameCount() {
        Captured c = captured.get(sourceStill());
        if (c == null || c.width == 0) {
            return 1;
        }
        return Math.max(1, c.height / c.width);
    }

    public void fillPreview(NativeImage target, int previewColor, int frame) {
        Captured c = captured.get(sourceStill());
        if (c == null) {
            return;
        }
        int tint = enabled ? previewColor : (useWaterTexture ? WATER_TINT : previewColor);
        int n = c.width;
        int frameCount = Math.max(1, c.height / n);
        int f = ((frame % frameCount) + frameCount) % frameCount;
        int offsetY = f * n;
        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
                int src = c.pristine[(offsetY + y) * n + x];
                target.setPixel(x, y, colorize(src, tint));
            }
        }
    }
}
