package com.pureblue.woad.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.List;
import java.util.Objects;

/**
 * Tooltips: a dark rounded box with a thin blue edge, shown after the pointer rests on something.
 *
 * <p>During a frame, whatever is hovered calls {@link #offer} with a key identifying it; at the
 * end of the frame {@link #render} draws the box on top of everything. The delay restarts when the
 * key changes, so moving between two items does not flash tooltips.
 */
public class UiTooltip {

    private Object key;
    private Object offeredKey;
    private List<String> offeredLines;
    private double since;
    private final Anim.Toggle fade = new Anim.Toggle(Theme.MS_TOOLTIP, Anim.Ease.OUT_CUBIC);
    private List<String> shown = List.of();

    /** Proposes a tooltip for this frame. The first line is drawn as a title. */
    public void offer(Object key, List<String> lines) {
        offeredKey = key;
        offeredLines = lines;
    }

    public void render(GuiGraphicsExtractor ctx, int mouseX, int mouseY, int screenW, int screenH) {
        double now = Anim.nowMs();
        if (!Objects.equals(offeredKey, key)) {
            key = offeredKey;
            since = now;
        }
        boolean wanted = key != null && now - since >= Theme.MS_TOOLTIP_DELAY;
        if (wanted) shown = offeredLines;
        float a = fade.update(wanted);
        offeredKey = null;
        offeredLines = null;
        if (a <= 0.01f || shown == null || shown.isEmpty()) return;

        int maxTextW = 220;
        int lineH = UiText.Style.BODY.lineHeight;
        int textW = 0;
        for (int i = 0; i < shown.size(); i++) {
            UiText.Style style = i == 0 ? UiText.Style.LABEL : UiText.Style.BODY;
            textW = Math.max(textW, Math.min(maxTextW, UiText.width(shown.get(i), style)));
        }
        float w = textW + 16;
        float h = shown.size() * lineH + 8;
        float x = mouseX + 10;
        float y = mouseY + 12 - 3 * (1 - a);
        if (x + w > screenW - 4) x = mouseX - 10 - w;
        if (y + h > screenH - 4) y = mouseY - 8 - h;
        x = Math.max(4, x);
        y = Math.max(4, y);

        ctx.nextStratum(); // above items and text drawn earlier in the frame
        float previous = Draw.pushAlpha(a);
        Draw.shadow(ctx, x, y, w, h, 5f, 6f, 0f, Draw.withAlpha(Theme.GLOW, 0.35f));
        Draw.roundRect(ctx, x, y, w, h, 5f, Theme.TOOLTIP_BG);
        Draw.outline(ctx, x, y, w, h, 5f, 1f, Theme.TOOLTIP_EDGE);
        for (int i = 0; i < shown.size(); i++) {
            UiText.Style style = i == 0 ? UiText.Style.LABEL : UiText.Style.BODY;
            String line = UiText.ellipsize(shown.get(i), style, maxTextW);
            UiText.draw(ctx, line, style, x + 8, y + 5 + i * lineH, i == 0 ? Theme.TEXT : Theme.TEXT_2);
        }
        Draw.popAlpha(previous);
    }
}
