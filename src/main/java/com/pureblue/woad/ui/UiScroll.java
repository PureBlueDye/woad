package com.pureblue.woad.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * A vertically scrolling, clipped region with smooth scrolling and a thin scrollbar.
 *
 * <p>Use it around content drawn at {@code top - offset}:
 * <pre>
 *   float off = scroll.begin(ctx, x, y, w, h);
 *   ... draw rows starting at y - off ...
 *   scroll.end(ctx, contentHeight, mouseX, mouseY);
 * </pre>
 * The wheel sets a target and the view glides to it with a fixed half-life, so scrolling feels the
 * same at any frame rate. The thumb can also be dragged. The content height is only known once
 * the content has been drawn, so bounds use the previous frame's height — invisible in practice.
 */
public class UiScroll {

    /** How far one notch of the wheel scrolls, in GUI pixels. */
    private static final float NOTCH = 24f;

    private float x;
    private float y;
    private float w;
    private float h;
    private float content;
    private final Anim.Smooth smooth = new Anim.Smooth(Theme.MS_SCROLL_HALF_LIFE);
    private final Anim.Toggle barHover = new Anim.Toggle(Theme.MS_HOVER, Anim.Ease.OUT_CUBIC);
    private boolean dragging;
    private float dragFrom;
    private float dragOffset;

    private float maxScroll() {
        return Math.max(0f, content - h);
    }

    /** Clips to the region and returns the scroll offset to subtract from content positions. */
    public float begin(GuiGraphicsExtractor ctx, float x, float y, float w, float h) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        smooth.setTarget(Math.max(0f, Math.min(maxScroll(), smooth.target())));
        float offset = Math.max(0f, Math.min(maxScroll(), smooth.update()));
        ctx.enableScissor(Math.round(x), Math.round(y), Math.round(x + w), Math.round(y + h));
        return offset;
    }

    /** Ends the clip and draws the scrollbar when the content overflows. */
    public void end(GuiGraphicsExtractor ctx, float contentHeight, int mouseX, int mouseY) {
        ctx.disableScissor();
        this.content = contentHeight;
        if (maxScroll() <= 0f) return;

        float hv = barHover.update(dragging || overBar(mouseX, mouseY));
        float barW = Theme.SCROLLBAR_W;
        float bx = x + w - barW - 1;
        float thumbH = Math.max(14f, h * h / content);
        float thumbY = y + (h - thumbH) * (smooth.update() / maxScroll());
        Draw.roundRect(ctx, bx, y, barW, h, barW / 2f, Draw.withAlpha(Theme.LINE, 0.6f + 0.4f * hv));
        Draw.roundRect(ctx, bx, thumbY, barW, thumbH, barW / 2f,
                Draw.withAlpha(Theme.ACCENT, 0.55f + 0.45f * hv));
    }

    public void scrollToTop() {
        smooth.jump(0f);
    }

    private boolean overBar(double mx, double my) {
        return mx >= x + w - 8 && mx < x + w && my >= y && my < y + h;
    }

    public boolean mouseScrolled(double mx, double my, double amount) {
        if (maxScroll() <= 0f || mx < x || mx >= x + w || my < y || my >= y + h) return false;
        smooth.setTarget(Math.max(0f, Math.min(maxScroll(), smooth.target() - (float) amount * NOTCH)));
        return true;
    }

    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0 || maxScroll() <= 0f || !overBar(mx, my)) return false;
        dragging = true;
        dragFrom = (float) my;
        dragOffset = smooth.target();
        return true;
    }

    public boolean mouseDragged(double mx, double my, int button) {
        if (!dragging) return false;
        float thumbH = Math.max(14f, h * h / content);
        float perPixel = maxScroll() / Math.max(1f, h - thumbH);
        smooth.jump(Math.max(0f, Math.min(maxScroll(), dragOffset + ((float) my - dragFrom) * perPixel)));
        return true;
    }

    public boolean mouseReleased(double mx, double my, int button) {
        boolean was = dragging;
        dragging = false;
        return was;
    }
}
