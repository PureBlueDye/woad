package com.pureblue.woad.lava;

import net.fabricmc.loader.api.FabricLoader;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockTintSources;
import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.client.renderer.block.FluidStateModelSet;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.sprite.AtlasManager;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.client.resources.model.sprite.SpriteId;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;

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
 * <p>Rendering goes through {@link #modelFor}: the chunk mesher asks {@code FluidStateModelSet} for
 * the model to draw a fluid with, and we answer with water's model, a tinted one, or both. Nothing
 * is painted into the texture atlas, so a change costs a chunk re-mesh
 * ({@link net.minecraft.client.renderer.LevelRenderer#allChanged()}) rather than a resource reload.
 *
 * <p>Sprite pixels are still captured, but only to draw the preview swatch in the menu — never to
 * render the world. Colour, the water-texture toggle and the custom palette are saved in
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

    /**
     * Our own sprites, shipped blank and filled at load with a brightness-only copy of whatever
     * lava texture the game is using.
     *
     * <p>They exist because a tint can only <em>multiply</em>. Lava's own texture is saturated
     * orange with almost nothing in the blue channel, so multiplying it can darken it but can never
     * turn it blue — and multiplying by white changes nothing at all, which is why white looked
     * broken. Water's texture is nearly grey, which is the only reason colours land correctly on
     * it. Giving lava a grey copy of itself gives it the same property, while keeping its own
     * shape and animation.
     */
    private static final Identifier TINT_STILL = Identifier.fromNamespaceAndPath("woad", "block/lava_tint_still");
    private static final Identifier TINT_FLOW  = Identifier.fromNamespaceAndPath("woad", "block/lava_tint_flow");

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

    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("Woad");

    private final Map<Identifier, Captured> captured = new HashMap<>();

    /** Sprites already complained about, so a per-chunk lookup cannot flood the log. */
    private final java.util.Set<Identifier> missingTintable =
            java.util.concurrent.ConcurrentHashMap.newKeySet();
    // Written from the menu on the render thread, read by modelFor() on the chunk-meshing workers.
    private volatile boolean enabled = false;
    private volatile int color = DEFAULT_COLOR; // 0xRRGGBB
    private boolean useWaterTexture = false;   // pending UI choice (button + preview)
    private volatile boolean appliedWaterTexture = false; // committed state used for rendering
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

    /** Commit the pending texture choice. Takes effect immediately (Apply). */
    public void commitTexture() {
        this.appliedWaterTexture = this.useWaterTexture;
        refresh();
    }

    private boolean isTracked(Identifier id) {
        return id.equals(LAVA_STILL) || id.equals(LAVA_FLOW)
            || id.equals(WATER_STILL) || id.equals(WATER_FLOW)
            || id.equals(TINT_STILL) || id.equals(TINT_FLOW);
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

    /** Called by the mixin for every sprite created. Only keeps lava/water and our own two. */
    public void tryCapture(Identifier id, NativeImage image) {
        if (!isTracked(id)) {
            return;
        }
        captured.put(id, new Captured(image));
        // Sprites are built in no particular order, so fill on every arrival: whichever of the
        // pair lands last completes it. This all happens inside the normal resource load, before
        // the atlas is uploaded, so it costs the player nothing.
        fillTintable(LAVA_STILL, TINT_STILL);
        fillTintable(LAVA_FLOW, TINT_FLOW);
    }

    /**
     * Writes a brightness-only copy of {@code source} into our blank sprite.
     *
     * <p>Each pixel keeps its alpha and becomes its own luminance, then the whole image is scaled
     * so its brightest pixel reaches white. Without that normalisation lava's mid-grey average
     * would swallow roughly half the chosen colour's brightness, and every colour would come out
     * looking muddy.
     *
     * <p>The two images are sampled rather than copied one-to-one, so a resource pack that ships
     * lava at a different resolution or frame count still works.
     */
    private void fillTintable(Identifier sourceId, Identifier targetId) {
        Captured source = captured.get(sourceId);
        Captured target = captured.get(targetId);
        if (source == null || target == null || source.width == 0 || target.width == 0) {
            return;
        }

        int peak = 1;
        for (int argb : source.pristine) {
            if (((argb >>> 24) & 0xFF) < 8) continue; // transparent: not part of the picture
            peak = Math.max(peak, luma(argb));
        }

        for (int y = 0; y < target.height; y++) {
            int sy = (int) ((long) y * source.height / target.height);
            for (int x = 0; x < target.width; x++) {
                int sx = (int) ((long) x * source.width / target.width);
                int argb = source.pristine[sy * source.width + sx];
                int grey = Math.min(255, luma(argb) * 255 / peak);
                target.image.setPixel(x, y,
                        ((argb >>> 24) & 0xFF) << 24 | (grey << 16) | (grey << 8) | grey);
            }
        }
    }

    /** Perceptual brightness of a pixel, ignoring its alpha. */
    private static int luma(int argb) {
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;
        return (r * 299 + g * 587 + b * 114) / 1000;
    }

    /** Turns the filter on with the given colour (0xRRGGBB). Takes effect immediately. */
    public void setColor(int rgb) {
        this.color = rgb & 0xFFFFFF;
        this.enabled = true;
        refresh();
        save();
    }

    /** Clears the colour filter only. Keeps the Lava/Water texture choice as-is
     *  (water with no colour falls back to the default biome water tint). */
    public void reset() {
        this.enabled = false;
        refresh();
        save();
    }

    // --- rendering ------------------------------------------------------------

    /**
     * The model lava should be drawn with, or {@code null} to leave the fluid alone.
     *
     * <p>Called from the chunk mesher for every fluid it meets, so it stays down to a couple of
     * reference comparisons when there is nothing to do. Asking the set for water's model re-enters
     * this method, which returns {@code null} at once because water is not lava.
     *
     * @param vanilla what the game was about to use, and the base for a colour in lava mode
     */
    public FluidModel modelFor(FluidStateModelSet models, FluidState state, FluidModel vanilla) {
        if (!enabled && !appliedWaterTexture) return null;
        if (state.getType() != Fluids.LAVA && state.getType() != Fluids.FLOWING_LAVA) return null;

        FluidModel base = appliedWaterTexture
                ? models.get(Fluids.WATER.defaultFluidState())
                : vanilla;
        if (base == null) return null;
        if (!enabled) return base; // water texture with no colour: keep water's own biome tint

        // On the lava texture, swap in our grey copy of it so the colour lands as chosen instead
        // of being multiplied into lava's orange. On the water texture there is nothing to fix.
        Material.Baked still = base.stillMaterial();
        Material.Baked flowing = base.flowingMaterial();
        if (!appliedWaterTexture) {
            Material.Baked greyStill = tintable(TINT_STILL);
            Material.Baked greyFlow = tintable(TINT_FLOW);
            if (greyStill != null) still = greyStill;
            if (greyFlow != null) flowing = greyFlow;
        }

        // The tint is an ARGB value, not a plain RGB one: BlockTintSources.constant takes the
        // colour used in hand and the colour used in the world, and both carry alpha. Masking it
        // off left alpha at zero, which made water-textured lava vanish outright and darkened the
        // rest — the tint multiplies the fluid's vertex colour, alpha channel included.
        int argb = 0xFF000000 | (color & 0xFFFFFF);
        return new FluidModel(
                base.layer(),
                still,
                flowing,
                base.overlayMaterial(),
                BlockTintSources.constant(argb, argb));
    }

    /**
     * One of our grey lava sprites, as a material the fluid model can draw.
     *
     * <p>{@code Material.Baked} is only a sprite plus a flag, so it can be built on the spot from
     * whatever the atlas stitched. Returns {@code null} before the atlas exists, or if the sprite
     * failed to register — the caller then keeps the vanilla material rather than drawing nothing.
     */
    private Material.Baked tintable(Identifier texture) {
        AtlasManager atlases = Minecraft.getInstance().getAtlasManager();
        if (atlases == null) return null;
        TextureAtlasSprite sprite = atlases.get(new SpriteId(TextureAtlas.LOCATION_BLOCKS, texture));
        if (sprite == null || !texture.equals(sprite.contents().name())) {
            // A missing sprite comes back as the "missing texture" placeholder, and falling back
            // to vanilla's material would just look like the colour is being ignored — say so.
            if (missingTintable.add(texture)) {
                LOGGER.warn("[Woad] {} is not in the block atlas; lava keeps its own texture and "
                        + "colours will only darken it", texture);
            }
            return null;
        }
        return new Material.Baked(sprite, false);
    }

    /**
     * Shows the current settings in the world.
     *
     * <p>Only the chunk meshes are rebuilt. The old implementation repainted the lava sprite and
     * needed {@code reloadResourcePacks()} — a full resource reload — for every colour change.
     */
    public void refresh() {
        Minecraft client = Minecraft.getInstance();
        if (client.levelRenderer != null) {
            client.levelRenderer.allChanged();
        }
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
