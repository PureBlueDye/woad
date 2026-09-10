package com.pureblue.woad.gui;

import com.pureblue.woad.config.ConfigStore;
import com.pureblue.woad.core.Woad;
import com.pureblue.woad.core.FeatureManager;
import com.pureblue.woad.features.JerryTimerFeature;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Edit screen for the Jerry Timer HUD: drag the HUD to move it, scroll to resize it. Changes are
 * saved when the screen is closed.
 */
public class HudEditScreen extends Screen {

    private static final int ACCENT = Woad.ACCENT;

    private boolean dragging = false;
    private double dragOffsetX;
    private double dragOffsetY;

    public HudEditScreen() {
        super(Component.literal("Woad HUD Editor"));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        super.extractRenderState(context, mouseX, mouseY, delta);
        context.fill(0, 0, this.width, this.height, 0x80000000);

        String hint = "Drag to move  •  Scroll to resize  •  Esc to save";
        context.text(this.font, hint,
                this.width / 2 - this.font.width(hint) / 2, 20, 0xFFCCCCCC, true);

        // Outline the editable area, then draw the HUD preview.
        int[] b = FeatureManager.JERRY_TIMER.getHudBounds();
        drawOutline(context, b[0] - 1, b[1] - 1, b[2] + 1, b[3] + 1, ACCENT);
        FeatureManager.JERRY_TIMER.renderHud(context);
    }

    private static void drawOutline(GuiGraphicsExtractor context, int x1, int y1, int x2, int y2, int color) {
        context.fill(x1, y1, x2, y1 + 1, color);   // top
        context.fill(x1, y2 - 1, x2, y2, color);   // bottom
        context.fill(x1, y1, x1 + 1, y2, color);   // left
        context.fill(x2 - 1, y1, x2, y2, color);   // right
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
