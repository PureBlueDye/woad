package com.pureblue.woad.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * A choice among several values: shows the current one, and a click unfolds the list right under
 * it — on the same screen, over whatever is below — to pick another.
 *
 * <p>The list is drawn by the owning screen at the very end of its frame ({@link #renderList}), so
 * it sits on top of everything, and the screen hands it clicks and scroll first while it is open
 * ({@link #listMouseClicked}, {@link #listMouseScrolled}). Long lists show a few rows at a time and
 * scroll with the wheel. Options are read each time the list opens, so a list that changes — the
 * prompt files in a folder — is always current.
 *
 * <p>The button half can be skipped entirely: a screen that already has its own button (a vanilla
 * one, on the server list) just gives the dropdown that button's bounds and calls {@link #toggle}.
 */
public class UiDropdown extends UiWidget {

    /** Rows shown before the list starts scrolling. */
    private static final int MAX_ROWS = 6;
    private static final int ROW_H = 14;
    private static final int LIST_PAD = 3;

    private final Supplier<List<String>> options;
    private final Supplier<String> value;
    private final Consumer<String> onSelect;
    private Function<String, String> label = Function.identity();

    private final Anim.Toggle opening = new Anim.Toggle(Theme.MS_HOVER + 40, Anim.Ease.OUT_CUBIC);
    private final Anim.Pulse press = new Anim.Pulse(Theme.MS_PRESS, Theme.MS_HOVER);
    private boolean open;
    /** The options as they were when the list opened. */
    private List<String> shown = List.of();
    /** Index of the first visible row, when there are more than {@link #MAX_ROWS}. */
    private int first;

    // Where the list was drawn this frame, for hit-testing.
    private float listX;
    private float listY;
    private float listW;
    private float listH;

    /**
     * @param options  the values to choose from, read each time the list opens
     * @param value    the current value
     * @param onSelect called with the value picked; not called when the list is closed without a pick
     */
    public UiDropdown(Supplier<List<String>> options, Supplier<String> value, Consumer<String> onSelect) {
        this.options = options;
        this.value = value;
        this.onSelect = onSelect;
    }

    /** How a value is shown, when it is an id rather than something to read. */
    public UiDropdown label(Function<String, String> label) {
        this.label = label;
        return this;
    }

    public boolean isOpen() {
        return open;
    }

    public void close() {
        open = false;
    }

    /** Opens the list, or closes it when it is already open. */
    public void toggle() {
        if (open) {
            open = false;
            return;
        }
        shown = List.copyOf(options.get());
        if (shown.isEmpty()) return;
        opening.update(false); // start the unfolding from closed, even on the very first open
        open = true;
        // Start with the current value in view.
        int current = Math.max(0, shown.indexOf(value.get()));
        first = Math.max(0, Math.min(current - MAX_ROWS / 2, shown.size() - MAX_ROWS));
    }

    public int preferredWidth(int min, int max) {
        int text = UiText.width(label.apply(value.get()), UiText.Style.BODY);
        return Math.max(min, Math.min(max, text + 28));
    }

    // ---- The button ----------------------------------------------------------------------------

    @Override
    protected void draw(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float hv) {
        if (!enabled) open = false;
        float o = opening.update(open);
        float r = Theme.RADIUS_CONTROL;
        float p = press.value();
        float lit = Math.max(hv, o);

        int fill = enabled ? Draw.mix(Theme.INSET, Theme.INSET_HOVER, lit) : Theme.INSET;
        Draw.roundRect(ctx, x, y, w, h, r, Draw.mix(fill, Theme.HOVER, p * 0.5f));
        Draw.outline(ctx, x, y, w, h, r, 1f,
                enabled ? Draw.mix(Theme.LINE_STRONG, Draw.withAlpha(Theme.ACCENT, 0.6f + 0.4f * o), lit) : Theme.LINE);

        int textColour = enabled ? Theme.TEXT : Theme.TEXT_3;
        String shownValue = UiText.ellipsize(label.apply(value.get()), UiText.Style.BODY, (int) (w - 22));
        UiText.draw(ctx, shownValue, UiText.Style.BODY, x + 7, UiText.centerY(UiText.Style.BODY, y, h), textColour);
        int iconColour = enabled ? Draw.mix(Theme.TEXT_2, Theme.TEXT, lit) : Theme.TEXT_3;
        Draw.chevronFlip(ctx, x + w - 8, y + h / 2f, 5f, o, iconColour);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (!enabled || button != 0 || !contains(mx, my)) return false;
        press.fire();
        clickSound();
        toggle();
        return true;
    }

    // ---- The list ------------------------------------------------------------------------------

    /**
     * Draws the open list under the button (or above it when the screen ends first). Call it last
     * in the frame, outside any scissor, so nothing covers it.
     */
    public void renderList(GuiGraphicsExtractor ctx, int mouseX, int mouseY, int screenW, int screenH) {
        // Advanced here too: with an outside button, draw() never runs.
        float o = opening.update(open);
        if (!open && o <= 0.01f) return;

        int rows = Math.min(MAX_ROWS, shown.size());
        float widest = 0;
        for (String option : shown) widest = Math.max(widest, UiText.width(label.apply(option), UiText.Style.BODY));
        listW = Math.max(w, Math.min(widest + 30, 180));
        listH = rows * ROW_H + LIST_PAD * 2;
        listX = Math.max(4, Math.min(x + w - listW, screenW - listW - 4)); // right edges aligned
        boolean below = y + h + 3 + listH <= screenH - 4 || y - 3 - listH < 4;
        listY = below ? y + h + 3 : y - 3 - listH;

        float slide = (1 - o) * 4 * (below ? -1 : 1);
        ctx.nextStratum(); // above the rows and text drawn earlier in the frame
        float previous = Draw.pushAlpha(o);
        float ly = listY + slide;
        Draw.shadow(ctx, listX, ly, listW, listH, 5f, 6f, 0f, Draw.withAlpha(Theme.GLOW, 0.3f));
        Draw.roundRect(ctx, listX, ly, listW, listH, 5f, Theme.TOOLTIP_BG);
        Draw.outline(ctx, listX, ly, listW, listH, 5f, 1f, Theme.TOOLTIP_EDGE);

        String current = value.get();
        boolean scrolls = shown.size() > MAX_ROWS;
        float rowW = listW - LIST_PAD * 2 - (scrolls ? 5 : 0);
        for (int i = 0; i < rows; i++) {
            int index = first + i;
            if (index >= shown.size()) break;
            String option = shown.get(index);
            float rx = listX + LIST_PAD;
            float ry = ly + LIST_PAD + i * ROW_H;
            boolean hovered = open && mouseX >= rx && mouseX < rx + rowW && mouseY >= ry && mouseY < ry + ROW_H;
            boolean selected = option.equals(current);
            if (selected) {
                Draw.roundRectH(ctx, rx, ry, rowW, ROW_H, 3f, Theme.SELECTED_FROM, Theme.SELECTED_TO);
            } else if (hovered) {
                Draw.roundRect(ctx, rx, ry, rowW, ROW_H, 3f, Theme.HOVER);
            }
            int colour = selected || hovered ? Theme.TEXT : Theme.TEXT_2;
            String text = UiText.ellipsize(label.apply(option), UiText.Style.BODY, (int) rowW - 22);
            UiText.draw(ctx, text, UiText.Style.BODY, rx + 6, UiText.centerY(UiText.Style.BODY, ry, ROW_H), colour);
            if (selected) Draw.check(ctx, rx + rowW - 8, ry + ROW_H / 2f, 6f, Theme.CYAN);
        }

        if (scrolls) {
            // A thin bar on the right showing which part of the list is in view.
            float trackY = ly + LIST_PAD + 1;
            float trackH = listH - LIST_PAD * 2 - 2;
            float thumbH = Math.max(8, trackH * MAX_ROWS / shown.size());
            float thumbY = trackY + (trackH - thumbH) * first / (float) (shown.size() - MAX_ROWS);
            float bx = listX + listW - LIST_PAD - 3;
            Draw.roundRect(ctx, bx, trackY, 2, trackH, 1f, Theme.TRACK);
            Draw.roundRect(ctx, bx, thumbY, 2, thumbH, 1f, Theme.ACCENT);
        }
        Draw.popAlpha(previous);
    }

    /**
     * A click while the list is open. Picking a row selects it; anywhere else just closes the
     * list — and the click is swallowed either way, so closing a list never presses something by
     * accident. A click on the button itself is left to {@link #mouseClicked}, which closes it.
     *
     * @return true when the click was used (always, while the list is open)
     */
    public boolean listMouseClicked(double mx, double my, int button) {
        if (!open) return false;
        if (contains(mx, my)) return false; // the button toggles it shut
        if (mx >= listX && mx < listX + listW && my >= listY + LIST_PAD && my < listY + listH - LIST_PAD) {
            int index = first + (int) ((my - listY - LIST_PAD) / ROW_H);
            if (button == 0 && index >= 0 && index < shown.size()) {
                clickSound();
                open = false;
                onSelect.accept(shown.get(index));
            }
            return true;
        }
        open = false;
        return true;
    }

    /**
     * Scrolls a long list by whole rows. Scrolling elsewhere closes the list instead and lets the
     * page behind scroll — the list would otherwise drift away from its button.
     *
     * @return true when the wheel was used by the list
     */
    public boolean listMouseScrolled(double mx, double my, double amount) {
        if (!open) return false;
        if (mx < listX || mx >= listX + listW || my < listY || my >= listY + listH) {
            open = false;
            return false;
        }
        if (shown.size() > MAX_ROWS) {
            first = Math.max(0, Math.min(shown.size() - MAX_ROWS, first - (int) Math.signum(amount)));
        }
        return true;
    }
}
