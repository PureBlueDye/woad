package com.pureblue.woad.features;

import com.google.gson.JsonObject;
import com.pureblue.woad.core.Feature;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.joml.Matrix3x2fStack;

import java.util.regex.Pattern;

/**
 * A 6-minute countdown for the Hidden Jerry cooldown, shown on a movable, resizable HUD.
 *
 * <p>While Mayor Jerry is in office a Hidden Jerry (Green, Blue, Purple or Golden) can appear, with
 * a 6-minute cooldown before the next one. The spawn is announced by one of several chat lines, all
 * prefixed with "☺" and containing "&lt;color&gt; Jerry" (per the Hypixel wiki), so we trigger on
 * that combination rather than a single fixed phrase — and exclude "Jerry Box" drops.
 *
 * <p>The HUD always shows "Jerry: " (gold) followed by the remaining time (white), or "Ready"
 * (green) once the cooldown is over. Its position and scale are edited with {@code /woad hud}.
 */
public class JerryTimerFeature extends Feature {

    private static final long DURATION_MS = 6 * 60 * 1000L;

    /** Matches any Hidden Jerry spawn line: the "☺" marker + a colored Jerry (but not a Jerry Box). */
    private static final Pattern JERRY_SPAWN =
            Pattern.compile("☺.*(?:Green|Blue|Purple|Golden) Jerry(?! Box)");

    private static final String LABEL = "Jerry: ";
    private static final int LABEL_COLOR = 0xFFFFAA00; // gold/yellow (Jerry)
    private static final int TIME_COLOR = 0xFFFFFFFF;  // white
    private static final int READY_COLOR = 0xFF55FF55; // green

    /** 0 means no countdown is running (idle → shows "Ready"). */
    private long endMillis = 0L;

    // HUD placement (scaled GUI pixels) — edited via /woad hud, persisted.
    private int hudX = 10;
    private int hudY = 10;
    private float hudScale = 1.5f;

    public JerryTimerFeature() {
        super("jerry_timer", "Jerry Timer",
                "Shows a 6-minute Hidden Jerry cooldown on a movable HUD (edit with /woad hud). "
                        + "Shows \"Ready\" when it is up.",
                true);
    }

    @Override
    public void onChatMessage(String message) {
        if (JERRY_SPAWN.matcher(message).find()) {
            endMillis = System.currentTimeMillis() + DURATION_MS;
        }
    }

    @Override
    protected void onDisabled() {
        endMillis = 0L;
    }

    // ---- HUD placement (used by the edit screen) ------------------------------------------

    public int getHudX() { return hudX; }
    public int getHudY() { return hudY; }
    public float getHudScale() { return hudScale; }

    public void setHudPos(int x, int y) { hudX = x; hudY = y; }

    public void setHudScale(float scale) {
        hudScale = Math.max(0.5f, Math.min(5.0f, scale));
    }

    /** Screen-space bounds {x1, y1, x2, y2} of the HUD text, for the editor's drag hit-test. */
    public int[] getHudBounds() {
        Font tr = Minecraft.getInstance().font;
        int w = tr.width(LABEL) + tr.width(currentValue());
        int h = tr.lineHeight;
        return new int[]{hudX, hudY,
                hudX + Math.round(w * hudScale), hudY + Math.round(h * hudScale)};
    }

    // ---- Rendering ------------------------------------------------------------------------

    /** Draws the HUD: "Jerry: " in gold + the time in white, or "Ready" in green. No background. */
    public void renderHud(GuiGraphicsExtractor context) {
        Font tr = Minecraft.getInstance().font;
        String value = currentValue();
        int valueColor = value.equals("Ready") ? READY_COLOR : TIME_COLOR;

        Matrix3x2fStack matrices = context.pose();
        matrices.pushMatrix();
        matrices.translate((float) hudX, (float) hudY);
        matrices.scale(hudScale, hudScale);
        context.text(tr, LABEL, 0, 0, LABEL_COLOR, true);
        context.text(tr, value, tr.width(LABEL), 0, valueColor, true);
        matrices.popMatrix();
    }

    /** Current right-hand text: the remaining "M:SS" while counting, else "Ready". */
    private String currentValue() {
        if (endMillis != 0L) {
            long remaining = endMillis - System.currentTimeMillis();
            if (remaining > 0) {
                long totalSeconds = (long) Math.ceil(remaining / 1000.0);
                long minutes = totalSeconds / 60;
                long seconds = totalSeconds % 60;
                return minutes + ":" + (seconds < 10 ? "0" + seconds : Long.toString(seconds));
            }
        }
        return "Ready";
    }

    // ---- Persistence ----------------------------------------------------------------------

    @Override
    public void writeConfig(JsonObject node) {
        node.addProperty("hudX", hudX);
        node.addProperty("hudY", hudY);
        node.addProperty("hudScale", hudScale);
    }

    @Override
    public void readConfig(JsonObject node) {
        if (node.has("hudX")) hudX = node.get("hudX").getAsInt();
        if (node.has("hudY")) hudY = node.get("hudY").getAsInt();
        if (node.has("hudScale")) setHudScale(node.get("hudScale").getAsFloat());
    }
}
