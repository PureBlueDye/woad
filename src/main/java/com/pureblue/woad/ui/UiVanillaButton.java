package com.pureblue.woad.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.Supplier;

/**
 * A real game {@link Button} drawn in Woad's style, for the few controls the mod adds to the
 * game's own screens — the connection picker on the server list, for instance.
 *
 * <p>Being a genuine widget, it keeps everything a vanilla button has there: keyboard focus, tab
 * navigation, narration. Only its look changes, along with its tooltip, which it draws itself so
 * the style matches.
 */
public class UiVanillaButton extends Button {

    private final Anim.Toggle hover = new Anim.Toggle(Theme.MS_HOVER, Anim.Ease.OUT_CUBIC);
    private final UiTooltip tooltip = new UiTooltip();
    private Supplier<List<String>> tooltipLines;

    public UiVanillaButton(int x, int y, int width, int height, Component message, OnPress onPress) {
        super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
    }

    /** Lines shown after the pointer rests on the button; the first is the title. */
    public UiVanillaButton tooltip(Supplier<List<String>> lines) {
        this.tooltipLines = lines;
        return this;
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        float x = getX();
        float y = getY();
        float w = getWidth();
        float h = getHeight();
        float hv = hover.update(active && isHoveredOrFocused());
        float r = Theme.RADIUS_CONTROL;

        if (hv > 0.01f) Draw.shadow(ctx, x, y, w, h, r, 4f, 0f, Draw.withAlpha(Theme.GLOW, 0.6f * hv));
        Draw.roundRectV(ctx, x, y, w, h, r,
                Draw.mix(0xEB16213A, Theme.RAISE, hv), Draw.mix(0xEB111A2E, Theme.HOVER, hv));
        Draw.outline(ctx, x, y, w, h, r, 1f, Draw.mix(Theme.LINE_STRONG, Theme.ACCENT, hv), Theme.LINE_STRONG);

        String label = UiText.ellipsize(getMessage().getString(), UiText.Style.LABEL, (int) w - 12);
        UiText.drawCentered(ctx, label, UiText.Style.LABEL, x + w / 2f,
                UiText.centerY(UiText.Style.LABEL, y, h), active ? Theme.TEXT : Theme.TEXT_3);

        if (tooltipLines != null && isHovered()) tooltip.offer(this, tooltipLines.get());
        Minecraft mc = Minecraft.getInstance();
        tooltip.render(ctx, mouseX, mouseY, mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());
    }
}
