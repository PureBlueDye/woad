package com.pureblue.woad.features;

import com.google.gson.JsonObject;
import com.pureblue.woad.core.Feature;
import com.pureblue.woad.ui.Draw;
import com.pureblue.woad.ui.Theme;
import com.pureblue.woad.ui.UiPanel;
import com.pureblue.woad.ui.UiText;
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
 * <p>The HUD is a small dark pill: a status dot, "Jerry:" and the remaining time, or "Ready" (green)
 * once the cooldown is over. Its position and scale are edited with {@code /woad hud}.
 */
public class JerryTimerFeature extends Feature {

    private static final long DURATION_MS = 6 * 60 * 1000L;

    /** Matches any Hidden Jerry spawn line: the "☺" marker + a colored Jerry (but not a Jerry Box). */
    private static final Pattern JERRY_SPAWN =
            Pattern.compile("☺.*(?:Green|Blue|Purple|Golden) Jerry(?! Box)");

    private static final String LABEL = "Jerry:";

    // Pill geometry, before the player's HUD scale is applied (GUI pixels).
    private static final float PILL_H = 14f;
    private static final float TEXT_X = 13f;
    private static final float VALUE_GAP = 3f;
    private static final float PAD_RIGHT = 7f;

    /** 0 means no countdown is running (idle → shows "Ready"). */
    private long endMillis = 0L;

    // HUD placement (scaled GUI pixels) — edited via /woad hud, persisted.
    private int hudX = 10;
    private int hudY = 10;
    private float hudScale = 1.5f;

    public JerryTimerFeature() {
        super("jerry_timer", "Jerry Timer", "",
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

    /** Screen-space bounds {x1, y1, x2, y2} of the HUD, for the editor's drag hit-test. */
    public int[] getHudBounds() {
        // Measured in the rasterisation the HUD is drawn with, so the frame hugs it exactly.
        float previous = Draw.pushScale(hudScale);
        float w = pillWidth(currentValue());
        Draw.popScale(previous);
        return new int[]{hudX, hudY,
                hudX + Math.round(w * hudScale), hudY + Math.round(PILL_H * hudScale)};
    }

    /** Unscaled width of the pill for a given value; drawing and hit-testing both use it. */
    private static float pillWidth(String value) {
        return TEXT_X + UiText.width(LABEL, UiText.Style.BODY) + VALUE_GAP
                + UiText.width(value, UiText.Style.LABEL) + PAD_RIGHT;
    }

    // ---- Rendering ------------------------------------------------------------------------

    /** Draws the HUD pill: a status dot, "Jerry:", and the time — or "Ready" in green. */
    public void renderHud(GuiGraphicsExtractor context) {
        String value = currentValue();
        boolean ready = value.equals("Ready");

        Matrix3x2fStack matrices = context.pose();
        matrices.pushMatrix();
        matrices.translate((float) hudX, (float) hudY);
        matrices.scale(hudScale, hudScale);
        // The HUD is enlarged by the player's own scale: rasterise its text for that size, or the
        // glyphs are stretched and look pixelated.
        float previousScale = Draw.pushScale(hudScale);
        float w = pillWidth(value);
        float r = PILL_H / 2f;
        Draw.roundRect(context, 0, 0, w, PILL_H, r, Theme.HUD_BG);
        Draw.outline(context, 0, 0, w, PILL_H, r, 1f, ready ? Draw.withAlpha(Theme.OK, 0.45f) : Theme.LINE_STRONG);
        UiPanel.dot(context, 7f, PILL_H / 2f, true, ready ? Theme.OK : Theme.CYAN);
        UiText.draw(context, LABEL, UiText.Style.BODY, TEXT_X, UiText.centerY(UiText.Style.BODY, 0, PILL_H), Theme.TEXT_2);
        float valueX = TEXT_X + UiText.width(LABEL, UiText.Style.BODY) + VALUE_GAP;
        UiText.draw(context, value, UiText.Style.LABEL, valueX, UiText.centerY(UiText.Style.LABEL, 0, PILL_H),
                ready ? Theme.OK : Theme.TEXT);
        Draw.popScale(previousScale);
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
