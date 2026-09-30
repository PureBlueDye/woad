package com.pureblue.woad.gui;

import com.pureblue.woad.config.ConfigStore;
import com.pureblue.woad.core.FeatureManager;
import com.pureblue.woad.features.JerryTimerFeature;
import com.pureblue.woad.ui.Anim;
import com.pureblue.woad.ui.Draw;
import com.pureblue.woad.ui.Theme;
import com.pureblue.woad.ui.UiScreen;
import com.pureblue.woad.ui.UiText;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * Edit screen for the Jerry Timer HUD: drag the HUD to move it, scroll to resize it. Changes are
 * saved when the screen is closed.
 *
 * <p>The world stays clearly visible here — only lightly dimmed — since the point is to place the
 * HUD against the game itself.
 */
public class HudEditScreen extends UiScreen {

    private boolean dragging = false;
    private double dragOffsetX;
    private double dragOffsetY;
    private final Anim.Toggle hover = new Anim.Toggle(Theme.MS_HOVER, Anim.Ease.OUT_CUBIC);

    public HudEditScreen() {
        super(Component.literal("Woad HUD Editor"));
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        Draw.rect(ctx, 0, 0, this.width, this.height, 0x4D0B1220);
    }

    @Override
    protected void renderContent(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        // Instructions, in a pill at the top.
        String hint = "Drag to move  ·  Scroll to resize  ·  Esc to save";
        float hintW = UiText.width(hint, UiText.Style.LABEL) + 20;
        float hx = (this.width - hintW) / 2f;
        Draw.roundRect(ctx, hx, 12, hintW, 18, 9f, Theme.TOOLTIP_BG);
        Draw.outline(ctx, hx, 12, hintW, 18, 9f, 1f, Theme.LINE_STRONG);
        UiText.draw(ctx, hint, UiText.Style.LABEL, hx + 10, UiText.centerY(UiText.Style.LABEL, 12, 18), Theme.TEXT);

        // The HUD itself, with a lit frame showing the area that can be grabbed.
        int[] b = FeatureManager.JERRY_TIMER.getHudBounds();
        boolean over = mouseX >= b[0] && mouseX <= b[2] && mouseY >= b[1] && mouseY <= b[3];
        float h = hover.update(over || dragging);
        float pad = 3f;
        float fx = b[0] - pad;
        float fy = b[1] - pad;
        float fw = b[2] - b[0] + pad * 2;
        float fh = b[3] - b[1] + pad * 2;
        Draw.shadow(ctx, fx, fy, fw, fh, 6f, 5f, 0f, Draw.withAlpha(Theme.GLOW, 0.4f + 0.6f * h));
        Draw.outline(ctx, fx, fy, fw, fh, 6f, 1f, Draw.mix(Draw.withAlpha(Theme.ACCENT, 0.7f), Theme.CYAN, h));
        FeatureManager.JERRY_TIMER.renderHud(ctx);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        if (click.button() == 0) {
            int[] b = FeatureManager.JERRY_TIMER.getHudBounds();
            if (click.x() >= b[0] && click.x() <= b[2] && click.y() >= b[1] && click.y() <= b[3]) {
                dragging = true;
                dragOffsetX = click.x() - b[0];
                dragOffsetY = click.y() - b[1];
                return true;
            }
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent click, double offsetX, double offsetY) {
        if (dragging) {
            int[] b = FeatureManager.JERRY_TIMER.getHudBounds();
            int boxW = b[2] - b[0];
            int boxH = b[3] - b[1];
            int nx = (int) Math.round(click.x() - dragOffsetX);
            int ny = (int) Math.round(click.y() - dragOffsetY);
            nx = Math.max(0, Math.min(this.width - boxW, nx));
            ny = Math.max(0, Math.min(this.height - boxH, ny));
            FeatureManager.JERRY_TIMER.setHudPos(nx, ny);
            return true;
        }
        return super.mouseDragged(click, offsetX, offsetY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent click) {
        if (click.button() == 0 && dragging) {
            dragging = false;
            return true;
        }
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (verticalAmount != 0) {
            JerryTimerFeature feature = FeatureManager.JERRY_TIMER;
            feature.setHudScale(feature.getHudScale() + (float) verticalAmount * 0.1f);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public void onClose() {
        ConfigStore.save();
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
