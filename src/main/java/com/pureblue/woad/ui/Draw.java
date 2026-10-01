package com.pureblue.woad.ui;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.pureblue.woad.mixin.GuiGraphicsExtractorAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fc;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Shape drawing for Woad's interface: rounded rectangles, gradients in any direction, soft
 * shadows, outlines, circles and stroked icons.
 *
 * <p>The game's public GUI API offers only axis-aligned fills and vertical two-colour gradients.
 * Everything here instead builds its own geometry — quads with float positions and a colour per
 * vertex — and hands it to the frame's render state exactly as vanilla does for its own
 * rectangles, through the same {@code GUI} pipeline. No shader, no texture.
 *
 * <p>Curved edges get a one-physical-pixel fringe that fades to transparent, which is what keeps
 * them smooth instead of stair-stepped at every GUI scale. All coordinates are GUI pixels and go
 * through the current pose, so shapes follow any transform a screen applies.
 */
public final class Draw {

    private Draw() {}

    // ---- Global fade ---------------------------------------------------------------------------

    /** Opacity applied to everything drawn through {@link Draw} and {@link UiText}. */
    private static float alpha = 1f;

    /** Multiplies the global opacity, e.g. while a screen fades in. Returns the previous value. */
    public static float pushAlpha(float multiplier) {
        float previous = alpha;
        alpha = previous * Anim.clamp01(multiplier);
        return previous;
    }

    public static void popAlpha(float previous) {
        alpha = previous;
    }

    /** Applies the global opacity to an ARGB colour. */
    public static int fade(int argb) {
        if (alpha >= 1f) return argb;
        int a = Math.round(((argb >>> 24) & 0xFF) * alpha);
        return (a << 24) | (argb & 0xFFFFFF);
    }

    // ---- Extra magnification -------------------------------------------------------------------

    /**
     * Magnification applied through the pose on top of the GUI scale — the player's HUD scale, a
     * card's enlarged suit. Text is rasterised for it and edges stay one physical pixel wide, so
     * enlarged elements stay sharp instead of being stretched.
     */
    private static float renderScale = 1f;

    /** Declares extra magnification until {@link #popScale}; returns the previous value. */
    public static float pushScale(float multiplier) {
        float previous = renderScale;
        renderScale = previous * Math.max(0.05f, multiplier);
        return previous;
    }

    public static void popScale(float previous) {
        renderScale = previous;
    }

    /** Screen pixels per GUI pixel at this point of the drawing, magnification included. */
    public static float effectiveScale() {
        return guiScale() * renderScale;
    }

    // ---- Colour helpers ------------------------------------------------------------------------

    public static int withAlpha(int argb, float a) {
        int base = (argb >>> 24) & 0xFF;
        return (Math.round(base * Anim.clamp01(a)) << 24) | (argb & 0xFFFFFF);
    }

    /** Linear blend between two ARGB colours, alpha included. */
    public static int mix(int from, int to, float t) {
        t = Anim.clamp01(t);
        int a = channel(from, to, 24, t);
        int r = channel(from, to, 16, t);
        int g = channel(from, to, 8, t);
        int b = channel(from, to, 0, t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int channel(int from, int to, int shift, float t) {
        int x = (from >>> shift) & 0xFF;
        int y = (to >>> shift) & 0xFF;
        return Math.round(x + (y - x) * t) & 0xFF;
    }

    // ---- Straight shapes -----------------------------------------------------------------------

    public static void rect(GuiGraphicsExtractor ctx, float x0, float y0, float x1, float y1, int color) {
        gradient(ctx, x0, y0, x1, y1, color, color, color, color);
    }

    /** Left-to-right gradient. The game's own gradient only runs top-to-bottom. */
    public static void gradientH(GuiGraphicsExtractor ctx, float x0, float y0, float x1, float y1, int left, int right) {
        gradient(ctx, x0, y0, x1, y1, left, right, left, right);
    }

    public static void gradientV(GuiGraphicsExtractor ctx, float x0, float y0, float x1, float y1, int top, int bottom) {
        gradient(ctx, x0, y0, x1, y1, top, top, bottom, bottom);
    }

    /** A rectangle with a colour at each corner: top-left, top-right, bottom-left, bottom-right. */
    public static void gradient(GuiGraphicsExtractor ctx, float x0, float y0, float x1, float y1,
                                int tl, int tr, int bl, int br) {
        if (x1 <= x0 || y1 <= y0) return;
        Shape s = new Shape(4);
        s.quad(x0, y0, fade(tl), x0, y1, fade(bl), x1, y1, fade(br), x1, y0, fade(tr));
        s.submit(ctx);
    }

    // ---- Rounded rectangles ----------------------------------------------------------------------

    public static void roundRect(GuiGraphicsExtractor ctx, float x, float y, float w, float h, float r, int color) {
        roundRect(ctx, x, y, w, h, r, color, color, color, color);
    }

    public static void roundRectV(GuiGraphicsExtractor ctx, float x, float y, float w, float h, float r, int top, int bottom) {
        roundRect(ctx, x, y, w, h, r, top, top, bottom, bottom);
    }

    public static void roundRectH(GuiGraphicsExtractor ctx, float x, float y, float w, float h, float r, int left, int right) {
        roundRect(ctx, x, y, w, h, r, left, right, left, right);
    }

    /** Diagonal gradient from the top-left corner to the bottom-right one. */
    public static void roundRectDiagonal(GuiGraphicsExtractor ctx, float x, float y, float w, float h, float r, int from, int to) {
        int middle = mix(from, to, 0.5f);
        roundRect(ctx, x, y, w, h, r, from, middle, middle, to);
    }

    /**
     * A filled rounded rectangle with a colour per corner, blended across the surface. The shape
     * is a fan from the centre, so a two-way gradient interpolates exactly; a smooth edge fringe
     * finishes the outline.
     */
    public static void roundRect(GuiGraphicsExtractor ctx, float x, float y, float w, float h, float r,
                                 int tl, int tr, int bl, int br) {
        if (w <= 0 || h <= 0) return;
        r = Math.max(0f, Math.min(r, Math.min(w, h) / 2f));
        int segs = segments(r);
        float[] ring = ring(x, y, w, h, r, 0f, segs);
        int n = ring.length / 2;
        float aa = pixel();

        Shape s = new Shape(n * 4 * 2);
        float cx = x + w / 2f;
        float cy = y + h / 2f;
        int cc = fade(bilinear(0.5f, 0.5f, tl, tr, bl, br));
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            float ax = ring[i * 2], ay = ring[i * 2 + 1];
            float bx = ring[j * 2], by = ring[j * 2 + 1];
            int ac = fade(colourAt(ax, ay, x, y, w, h, tl, tr, bl, br));
            int bc = fade(colourAt(bx, by, x, y, w, h, tl, tr, bl, br));
            s.tri(cx, cy, cc, ax, ay, ac, bx, by, bc);
        }
        if (r >= 0.5f) {
            float[] outer = ring(x, y, w, h, r, aa, segs);
            for (int i = 0; i < n; i++) {
                int j = (i + 1) % n;
                int ac = fade(colourAt(ring[i * 2], ring[i * 2 + 1], x, y, w, h, tl, tr, bl, br));
                int bc = fade(colourAt(ring[j * 2], ring[j * 2 + 1], x, y, w, h, tl, tr, bl, br));
                s.quad(ring[i * 2], ring[i * 2 + 1], ac,
                        outer[i * 2], outer[i * 2 + 1], ac & 0xFFFFFF,
                        outer[j * 2], outer[j * 2 + 1], bc & 0xFFFFFF,
                        ring[j * 2], ring[j * 2 + 1], bc);
            }
        }
        s.submit(ctx);
    }

    /** A rounded border of the given thickness, drawn inside the bounds. */
    public static void outline(GuiGraphicsExtractor ctx, float x, float y, float w, float h, float r,
                               float thickness, int color) {
        outline(ctx, x, y, w, h, r, thickness, color, color);
    }

    /** A rounded border whose colour runs from top to bottom — a lit top edge, for example. */
    public static void outline(GuiGraphicsExtractor ctx, float x, float y, float w, float h, float r,
                               float thickness, int top, int bottom) {
        if (w <= 0 || h <= 0 || thickness <= 0) return;
        r = Math.max(0f, Math.min(r, Math.min(w, h) / 2f));
        int segs = segments(r);
        float[] outer = ring(x, y, w, h, r, 0f, segs);
        float[] inner = ring(x, y, w, h, r, -thickness, segs);
        float aa = pixel();
        float[] fringe = r >= 0.5f ? ring(x, y, w, h, r, aa, segs) : null;
        int n = outer.length / 2;

        Shape s = new Shape(n * 4 * 2);
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            int oc = fade(mix(top, bottom, (outer[i * 2 + 1] - y) / h));
            int pc = fade(mix(top, bottom, (outer[j * 2 + 1] - y) / h));
            s.quad(inner[i * 2], inner[i * 2 + 1], oc,
                    outer[i * 2], outer[i * 2 + 1], oc,
                    outer[j * 2], outer[j * 2 + 1], pc,
                    inner[j * 2], inner[j * 2 + 1], pc);
            if (fringe != null) {
                s.quad(outer[i * 2], outer[i * 2 + 1], oc,
                        fringe[i * 2], fringe[i * 2 + 1], oc & 0xFFFFFF,
                        fringe[j * 2], fringe[j * 2 + 1], pc & 0xFFFFFF,
                        outer[j * 2], outer[j * 2 + 1], pc);
            }
        }
        s.submit(ctx);
    }

    /**
     * A soft shadow or glow around a rounded rectangle: bands of decreasing opacity growing
     * outward by {@code spread}, with a quadratic fall-off that reads as blurred.
     *
     * <p>Centred ({@code dy == 0}) only the outside is painted, which is what a glow needs. Dropped
     * ({@code dy != 0}) the shifted shape is filled as well: painting only its outside left an
     * empty strip between the shape and its shadow, which showed as a detached band underneath.
     * A dropped shadow therefore belongs under opaque shapes only.
     *
     * @param dy vertical offset, positive to drop the shadow below the shape
     */
    public static void shadow(GuiGraphicsExtractor ctx, float x, float y, float w, float h, float r,
                              float spread, float dy, int color) {
        if (w <= 0 || h <= 0 || spread <= 0 || ((color >>> 24) & 0xFF) == 0) return;
        y += dy;
        r = Math.max(0f, Math.min(r, Math.min(w, h) / 2f));
        int segs = segments(r + spread);
        int bands = 6;
        float[] prev = ring(x, y, w, h, r, 0f, segs);
        int n = prev.length / 2;
        Shape s = new Shape(n * 4 * (bands + 1));
        if (dy != 0f) {
            int inner = fade(color);
            float cx = x + w / 2f;
            float cy = y + h / 2f;
            for (int i = 0; i < n; i++) {
                int j = (i + 1) % n;
                s.tri(cx, cy, inner, prev[i * 2], prev[i * 2 + 1], inner, prev[j * 2], prev[j * 2 + 1], inner);
            }
        }
        for (int b = 1; b <= bands; b++) {
            float t0 = (b - 1) / (float) bands;
            float t1 = b / (float) bands;
            float[] next = ring(x, y, w, h, r, spread * t1, segs);
            int c0 = fade(withAlpha(color, (1 - t0) * (1 - t0)));
            int c1 = fade(withAlpha(color, (1 - t1) * (1 - t1)));
            for (int i = 0; i < n; i++) {
                int j = (i + 1) % n;
                s.quad(prev[i * 2], prev[i * 2 + 1], c0,
                        next[i * 2], next[i * 2 + 1], c1,
                        next[j * 2], next[j * 2 + 1], c1,
                        prev[j * 2], prev[j * 2 + 1], c0);
            }
            prev = next;
        }
        s.submit(ctx);
    }

    public static void circle(GuiGraphicsExtractor ctx, float cx, float cy, float radius, int color) {
        roundRect(ctx, cx - radius, cy - radius, radius * 2, radius * 2, radius, color);
    }

    /**
     * The lower half of an ellipse whose flat side runs along {@code top} — a card table seen from
     * above. The colour runs from {@code centre} at the middle of the flat edge out to {@code rim},
     * which reads as light falling on felt.
     */
    public static void halfEllipse(GuiGraphicsExtractor ctx, float cx, float top, float rx, float ry,
                                   int centre, int rim) {
        if (rx <= 0 || ry <= 0) return;
        int n = arcSegments(rx, ry);
        float aa = pixel();
        int c = fade(centre);
        int r = fade(rim);
        int clear = r & 0xFFFFFF;
        Shape s = new Shape(n * 8);
        for (int i = 0; i < n; i++) {
            double a0 = Math.PI * i / n;
            double a1 = Math.PI * (i + 1) / n;
            float x0 = cx + (float) Math.cos(a0) * rx, y0 = top + (float) Math.sin(a0) * ry;
            float x1 = cx + (float) Math.cos(a1) * rx, y1 = top + (float) Math.sin(a1) * ry;
            s.tri(cx, top, c, x0, y0, r, x1, y1, r);
            // Smooth the curved edge with a one-pixel fringe along its outward normal.
            float[] n0 = ellipseNormal(a0, rx, ry);
            float[] n1 = ellipseNormal(a1, rx, ry);
            s.quad(x0, y0, r, x0 + n0[0] * aa, y0 + n0[1] * aa, clear,
                    x1 + n1[0] * aa, y1 + n1[1] * aa, clear, x1, y1, r);
        }
        s.submit(ctx);
    }

    /** A stroke following the curved edge of {@link #halfEllipse}, e.g. a line painted on felt. */
    public static void halfEllipseStroke(GuiGraphicsExtractor ctx, float cx, float top, float rx, float ry,
                                         float thickness, int color) {
        if (rx <= 0 || ry <= 0) return;
        int n = arcSegments(rx, ry);
        float half = thickness / 2f;
        float aa = pixel();
        int c = fade(color);
        int clear = c & 0xFFFFFF;
        Shape s = new Shape(n * 12);
        for (int i = 0; i < n; i++) {
            double a0 = Math.PI * i / n;
            double a1 = Math.PI * (i + 1) / n;
            float x0 = cx + (float) Math.cos(a0) * rx, y0 = top + (float) Math.sin(a0) * ry;
            float x1 = cx + (float) Math.cos(a1) * rx, y1 = top + (float) Math.sin(a1) * ry;
            float[] n0 = ellipseNormal(a0, rx, ry);
            float[] n1 = ellipseNormal(a1, rx, ry);
            s.quad(x0 - n0[0] * half, y0 - n0[1] * half, c, x0 + n0[0] * half, y0 + n0[1] * half, c,
                    x1 + n1[0] * half, y1 + n1[1] * half, c, x1 - n1[0] * half, y1 - n1[1] * half, c);
            float o = half + aa;
            s.quad(x0 + n0[0] * half, y0 + n0[1] * half, c, x0 + n0[0] * o, y0 + n0[1] * o, clear,
                    x1 + n1[0] * o, y1 + n1[1] * o, clear, x1 + n1[0] * half, y1 + n1[1] * half, c);
            s.quad(x0 - n0[0] * half, y0 - n0[1] * half, c, x0 - n0[0] * o, y0 - n0[1] * o, clear,
                    x1 - n1[0] * o, y1 - n1[1] * o, clear, x1 - n1[0] * half, y1 - n1[1] * half, c);
        }
        s.submit(ctx);
    }

    private static int arcSegments(float rx, float ry) {
        return Math.max(16, Math.min(96, (int) Math.ceil(Math.sqrt(Math.max(rx, ry) * effectiveScale()) * 4)));
    }

    /** Outward unit normal of an ellipse at parameter angle {@code a}. */
    private static float[] ellipseNormal(double a, float rx, float ry) {
        float nx = (float) (Math.cos(a) / rx);
        float ny = (float) (Math.sin(a) / ry);
        float len = (float) Math.sqrt(nx * nx + ny * ny);
        return new float[]{nx / len, ny / len};
    }

    // ---- Strokes and icons ---------------------------------------------------------------------

    /** A straight stroke with smooth edges, for icons. */
    public static void line(GuiGraphicsExtractor ctx, float x0, float y0, float x1, float y1,
                            float thickness, int color) {
        float dx = x1 - x0, dy = y1 - y0;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 1e-3f) return;
        float nx = -dy / len, ny = dx / len;
        float half = thickness / 2f;
        float aa = pixel();
        int c = fade(color);
        int clear = c & 0xFFFFFF;
        Shape s = new Shape(12);
        s.quad(x0 + nx * half, y0 + ny * half, c, x0 - nx * half, y0 - ny * half, c,
                x1 - nx * half, y1 - ny * half, c, x1 + nx * half, y1 + ny * half, c);
        float o = half + aa;
        s.quad(x0 + nx * half, y0 + ny * half, c, x1 + nx * half, y1 + ny * half, c,
                x1 + nx * o, y1 + ny * o, clear, x0 + nx * o, y0 + ny * o, clear);
        s.quad(x0 - nx * half, y0 - ny * half, c, x0 - nx * o, y0 - ny * o, clear,
                x1 - nx * o, y1 - ny * o, clear, x1 - nx * half, y1 - ny * half, c);
        s.submit(ctx);
    }

    /** A down-pointing chevron centred on (cx, cy), as on a dropdown. */
    public static void chevronDown(GuiGraphicsExtractor ctx, float cx, float cy, float size, int color) {
        float h = size / 2f;
        line(ctx, cx - h, cy - h / 2f, cx, cy + h / 2f, 1.2f, color);
        line(ctx, cx, cy + h / 2f, cx + h, cy - h / 2f, 1.2f, color);
    }

    /**
     * A chevron that turns from pointing down ({@code flip} 0) to pointing up ({@code flip} 1), as a
     * dropdown's arrow does while its list opens.
     */
    public static void chevronFlip(GuiGraphicsExtractor ctx, float cx, float cy, float size, float flip, int color) {
        float h = size / 2f;
        float s = (1 - 2 * flip) * h / 2f; // tip below the centre, then above it
        line(ctx, cx - h, cy - s, cx, cy + s, 1.2f, color);
        line(ctx, cx, cy + s, cx + h, cy - s, 1.2f, color);
    }

    /** A right-pointing chevron: "go", "run", "open". */
    public static void chevronRight(GuiGraphicsExtractor ctx, float cx, float cy, float size, int color) {
        float h = size / 2f;
        line(ctx, cx - h / 2f, cy - h, cx + h / 2f, cy, 1.2f, color);
        line(ctx, cx + h / 2f, cy, cx - h / 2f, cy + h, 1.2f, color);
    }

    /** A cross, as on a close button. */
    public static void cross(GuiGraphicsExtractor ctx, float cx, float cy, float size, int color) {
        float h = size / 2f;
        line(ctx, cx - h, cy - h, cx + h, cy + h, 1.3f, color);
        line(ctx, cx - h, cy + h, cx + h, cy - h, 1.3f, color);
    }

    public static void plus(GuiGraphicsExtractor ctx, float cx, float cy, float size, int color) {
        float h = size / 2f;
        line(ctx, cx - h, cy, cx + h, cy, 1.3f, color);
        line(ctx, cx, cy - h, cx, cy + h, 1.3f, color);
    }

    public static void check(GuiGraphicsExtractor ctx, float cx, float cy, float size, int color) {
        float h = size / 2f;
        line(ctx, cx - h, cy, cx - h / 3f, cy + h * 0.66f, 1.3f, color);
        line(ctx, cx - h / 3f, cy + h * 0.66f, cx + h, cy - h * 0.6f, 1.3f, color);
    }

    /** A pencil, marking a value that opens an editor rather than cycling in place. */
    public static void pencil(GuiGraphicsExtractor ctx, float cx, float cy, float size, int color) {
        float h = size / 2f;
        line(ctx, cx - h + 1f, cy + h - 1f, cx + h - 1.5f, cy - h + 1.5f, 1.6f, color);
        line(ctx, cx - h, cy + h, cx - h + 1.6f, cy + h - 0.6f, 1.1f, color);
    }

    // ---- Geometry --------------------------------------------------------------------------------

    /** One physical pixel, in GUI pixels: the width of the anti-aliasing fringe. */
    private static float pixel() {
        return 1f / Math.max(0.5f, effectiveScale());
    }

    static int guiScale() {
        return Math.max(1, Minecraft.getInstance().getWindow().getGuiScale());
    }

    /** Points per quarter-circle: enough that no facet shows at the current GUI scale. */
    private static int segments(float r) {
        if (r < 0.5f) return 1;
        return Math.max(2, Math.min(16, (int) Math.ceil(Math.sqrt(r * effectiveScale()) * 1.7)));
    }

    /**
     * The outline of a rounded rectangle grown by {@code d} (negative to shrink), clockwise from
     * the left end of the top-left corner. Every ring built with the same {@code segs} has the same
     * point count, so two rings can be stitched together with quads.
     */
    private static float[] ring(float x, float y, float w, float h, float r, float d, int segs) {
        float cr = Math.max(r, -d);           // corner centres stay put until the shrink passes them
        float rr = cr + d;                    // radius of this ring's corners
        float[] out = new float[(segs + 1) * 4 * 2];
        float[] cxs = {x + cr, x + w - cr, x + w - cr, x + cr};
        float[] cys = {y + cr, y + cr, y + h - cr, y + h - cr};
        float[] start = {180f, 270f, 0f, 90f};
        int k = 0;
        for (int corner = 0; corner < 4; corner++) {
            for (int i = 0; i <= segs; i++) {
                double a = Math.toRadians(start[corner] + 90f * i / segs);
                out[k++] = cxs[corner] + (float) (Math.cos(a) * rr);
                out[k++] = cys[corner] + (float) (Math.sin(a) * rr);
            }
        }
        return out;
    }

    private static int colourAt(float px, float py, float x, float y, float w, float h,
                                int tl, int tr, int bl, int br) {
        return bilinear((px - x) / w, (py - y) / h, tl, tr, bl, br);
    }

    private static int bilinear(float u, float v, int tl, int tr, int bl, int br) {
        return mix(mix(tl, tr, u), mix(bl, br, u), v);
    }

    // ---- Submission ------------------------------------------------------------------------------

    /**
     * The package-private {@code ScissorStack} of {@link GuiGraphicsExtractor} cannot be named from
     * here, so an accessor cannot return it. Its current rectangle is read through method handles,
     * resolved once. 26.x runs unobfuscated, so the names are stable.
     */
    private static final MethodHandle SCISSOR_STACK;
    private static final MethodHandle SCISSOR_PEEK;

    static {
        MethodHandle stack = null;
        MethodHandle peek = null;
        try {
            Field field = GuiGraphicsExtractor.class.getDeclaredField("scissorStack");
            field.setAccessible(true);
            Method method = field.getType().getDeclaredMethod("peek");
            method.setAccessible(true);
            MethodHandles.Lookup lookup = MethodHandles.lookup();
            stack = lookup.unreflectGetter(field);
            peek = lookup.unreflect(method);
        } catch (ReflectiveOperationException | RuntimeException e) {
            org.slf4j.LoggerFactory.getLogger("Woad").warn("[Woad] scissor lookup failed; shapes will not clip", e);
        }
        SCISSOR_STACK = stack;
        SCISSOR_PEEK = peek;
    }

    private static ScreenRectangle scissor(GuiGraphicsExtractor ctx) {
        if (SCISSOR_STACK == null) return null;
        try {
            return (ScreenRectangle) SCISSOR_PEEK.invoke(SCISSOR_STACK.invoke(ctx));
        } catch (Throwable t) {
            return null;
        }
    }

    /** Collects quads for one shape, then submits them as a single element. */
    private static final class Shape {
        private float[] xy;
        private int[] colours;
        private int count;
        private float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE;
        private float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;

        Shape(int expectedVertices) {
            xy = new float[Math.max(8, expectedVertices) * 2];
            colours = new int[Math.max(8, expectedVertices)];
        }

        void vertex(float x, float y, int colour) {
            if (count == colours.length) {
                xy = java.util.Arrays.copyOf(xy, xy.length * 2);
                colours = java.util.Arrays.copyOf(colours, colours.length * 2);
            }
            xy[count * 2] = x;
            xy[count * 2 + 1] = y;
            colours[count++] = colour;
            if (x < minX) minX = x;
            if (y < minY) minY = y;
            if (x > maxX) maxX = x;
            if (y > maxY) maxY = y;
        }

        /**
         * Adds a quad in the winding the GUI pipeline keeps. It culls back faces, so a quad listed
         * the other way round simply never appears; rather than get every builder's corner order
         * right, the order is checked here and flipped when needed.
         */
        void quad(float ax, float ay, int ac, float bx, float by, int bc,
                  float cx, float cy, int cc, float dx, float dy, int dc) {
            float cross = (bx - ax) * (cy - ay) - (by - ay) * (cx - ax);
            if (cross > 0) {
                vertex(ax, ay, ac);
                vertex(dx, dy, dc);
                vertex(cx, cy, cc);
                vertex(bx, by, bc);
            } else {
                vertex(ax, ay, ac);
                vertex(bx, by, bc);
                vertex(cx, cy, cc);
                vertex(dx, dy, dc);
            }
        }

        /** The GUI pipeline draws quads; a triangle is a quad whose last corner repeats. */
        void tri(float ax, float ay, int ac, float bx, float by, int bc, float cx, float cy, int cc) {
            quad(ax, ay, ac, bx, by, bc, cx, cy, cc, cx, cy, cc);
        }

        void submit(GuiGraphicsExtractor ctx) {
            if (count == 0) return;
            Matrix3x2f pose = new Matrix3x2f(ctx.pose());
            ScreenRectangle clip = scissor(ctx);
            int x0 = (int) Math.floor(minX);
            int y0 = (int) Math.floor(minY);
            ScreenRectangle area = new ScreenRectangle(x0, y0,
                    Math.max(1, (int) Math.ceil(maxX) - x0), Math.max(1, (int) Math.ceil(maxY) - y0))
                    .transformMaxBounds(pose);
            ScreenRectangle bounds = clip != null ? clip.intersection(area) : area;
            if (clip != null && bounds == null) return; // entirely clipped away
            ((GuiGraphicsExtractorAccessor) ctx).woad$guiRenderState()
                    .addGuiElement(new ShapeState(xy, colours, count, pose, clip, bounds));
        }
    }

    /** One submitted shape, replayed into the vertex buffer when the frame is drawn. */
    private record ShapeState(float[] xy, int[] colours, int count, Matrix3x2fc pose,
                              ScreenRectangle scissorArea, ScreenRectangle bounds)
            implements GuiElementRenderState {

        @Override
        public void buildVertices(VertexConsumer consumer) {
            for (int i = 0; i < count; i++) {
                consumer.addVertexWith2DPose(pose, xy[i * 2], xy[i * 2 + 1]).setColor(colours[i]);
            }
        }

        @Override
        public RenderPipeline pipeline() {
            return RenderPipelines.GUI;
        }

        @Override
        public TextureSetup textureSetup() {
            return TextureSetup.noTexture();
        }
    }
}
