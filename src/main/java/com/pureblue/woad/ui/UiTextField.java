package com.pureblue.woad.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

/**
 * A text field: Woad's rounded frame around the game's own {@link EditBox}.
 *
 * <p>Editing — the cursor, selection, clipboard, keyboard shortcuts, scrolling long text — is
 * vanilla's, so it behaves exactly as players expect. The box is given {@link UiText}'s Inter
 * {@code Font}, which makes its measurements and its drawing agree, and its own border is turned
 * off in favour of this frame: an inset surface whose edge turns blue and glows when focused.
 *
 * <p>The screen must register {@link #box()} as a child (not as a renderable) so it receives key
 * presses and focus; this class draws it.
 */
public class UiTextField extends UiWidget {

    private static final int PAD_X = 6;

    private final EditBox box;
    private final Anim.Toggle focus = new Anim.Toggle(Theme.MS_FOCUS, Anim.Ease.OUT_CUBIC);
    private String placeholder = "";
    private float textY;
    private boolean invalid;

    public UiTextField(String value, int maxLength) {
        box = new EditBox(UiText.font(UiText.Style.BODY), 0, 0, 10, 10, Component.empty());
        box.setBordered(false);
        box.setMaxLength(maxLength);
        box.setValue(value == null ? "" : value);
        box.setTextColor(Theme.TEXT);
        box.setTextColorUneditable(Theme.TEXT_3);
        box.setTextShadow(false);
    }

    public EditBox box() {
        return box;
    }

    public String value() {
        return box.getValue();
    }

    public UiTextField value(String value) {
        box.setValue(value);
        return this;
    }

    public UiTextField placeholder(String placeholder) {
        this.placeholder = placeholder;
        return this;
    }

    /** Marks the content as rejected: the frame turns red until it is cleared. */
    public UiTextField invalid(boolean invalid) {
        this.invalid = invalid;
        return this;
    }

    @Override
    public UiWidget bounds(float x, float y, float w, float h) {
        super.bounds(x, y, w, h);
        box.setX(Math.round(x + PAD_X));
        // The box draws its text at a whole-pixel y; the remainder is applied as a translation
        // while drawing, so the text is centred on its capitals like every other label.
        textY = UiText.lineY(UiText.Style.BODY, UiText.centerY(UiText.Style.BODY, y, h));
        box.setY((int) Math.floor(textY));
        int innerW = Math.max(1, Math.round(w - PAD_X * 2));
        if (innerW != box.getWidth()) {
            box.setWidth(innerW);
            // The box works out which part of the text is visible when its cursor moves, using its
            // width at that moment; a width change alone does not redo it, so a field that started
            // narrow would keep showing its last character only. Moving to the start resets the
            // visible window; a focused field then gets its cursor back at the end, ready to type.
            box.moveCursorToStart(false);
            if (box.isFocused()) box.moveCursorToEnd(false);
        }
        box.setHeight(Math.max(1, Math.round(h / 2f)));
        return this;
    }

    @Override
    protected void draw(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float hv) {
        float f = focus.update(box.isFocused());
        float r = Theme.RADIUS_CONTROL;
        int edge = invalid ? Theme.ERR
                : Draw.mix(Draw.mix(Theme.LINE_STRONG, Theme.LINE_HOVER, hv), Theme.ACCENT, f);
        if (f > 0.01f || invalid) {
            int glow = invalid ? Draw.withAlpha(Theme.ERR, 0.35f) : Draw.withAlpha(Theme.GLOW, f);
            Draw.shadow(ctx, x, y, w, h, r, 4f, 0f, glow);
        }
        Draw.roundRect(ctx, x, y, w, h, r, Draw.mix(Theme.INSET, Theme.INSET_HOVER, hv));
        Draw.outline(ctx, x, y, w, h, r, 1f, edge);

        if (box.getValue().isEmpty() && !box.isFocused() && !placeholder.isEmpty()) {
            UiText.draw(ctx, placeholder, UiText.Style.BODY, x + PAD_X,
                    UiText.centerY(UiText.Style.BODY, y, h), Theme.TEXT_3);
        }
        ctx.enableScissor(Math.round(x + 2), Math.round(y), Math.round(x + w - 2), Math.round(y + h));
        float scale = Draw.guiScale();
        float fy = Math.round(textY * scale) / scale - (float) Math.floor(textY);
        ctx.pose().pushMatrix();
        ctx.pose().translate(0f, fy);
        box.extractRenderState(ctx, mouseX, mouseY, 0f);
        ctx.pose().popMatrix();
        ctx.disableScissor();
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        // The box itself handles clicks through the screen's child list; this only widens the
        // clickable area to the whole frame, padding included.
        if (!enabled || !contains(mx, my) || box.isMouseOver(mx, my)) return false;
        box.setFocused(true);
        return true;
    }
}
