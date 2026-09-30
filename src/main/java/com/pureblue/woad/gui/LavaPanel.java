package com.pureblue.woad.gui;

import com.mojang.blaze3d.platform.NativeImage;
import com.pureblue.woad.lava.LavaColorManager;
import com.pureblue.woad.ui.Anim;
import com.pureblue.woad.ui.Draw;
import com.pureblue.woad.ui.Theme;
import com.pureblue.woad.ui.UiButton;
import com.pureblue.woad.ui.UiPanel;
import com.pureblue.woad.ui.UiScreen;
import com.pureblue.woad.ui.UiSegmented;
import com.pureblue.woad.ui.UiText;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.util.List;

/**
 * The Custom Lava editor, drawn inside the Woad menu.
 *
 * <p>Five base colours, ten palette slots (empty ones show a "+" that opens a hex prompt,
 * right-click clears one), a live preview, the lava/water texture choice and a colour reset.
 * Every change shows in the world at once: colours re-tint the fluid model and the texture switch
 * swaps it, so there is no Apply step any more.
 */
public class LavaPanel {

    private static final int[] BASE = {0xFF0000, 0xFF7F00, 0xFFFF00, 0x00FF00, 0x0000FF};
    private static final int SWATCH = 15;
    private static final int GAP = 4;
    private static final int PAD = 12;
    private static final int PREVIEW = 70;
    private static final Identifier PREVIEW_ID = Identifier.fromNamespaceAndPath("woad", "lava_panel_preview");

    private int currentColor;

    // Hover progress per swatch: five base colours, then the ten palette slots.
    private final Anim.Toggle[] swatchHover = new Anim.Toggle[BASE.length + LavaColorManager.CUSTOM_SLOTS];
    private final float[][] swatchRects = new float[BASE.length + LavaColorManager.CUSTOM_SLOTS][];

    private final UiSegmented texture = new UiSegmented(List.of("Lava", "Water"),
            () -> LavaColorManager.INSTANCE.isUseWaterTexture() ? 1 : 0,
            index -> {
                LavaColorManager.INSTANCE.setUseWaterTexture(index == 1);
                LavaColorManager.INSTANCE.commitTexture();
            });
    private final UiButton reset = new UiButton("Reset colour", UiButton.Variant.SECONDARY, () -> {
        LavaColorManager.INSTANCE.reset();
        currentColor = LavaColorManager.DEFAULT_COLOR;
    });

    // Live preview texture.
    private NativeImage previewImg;
    private DynamicTexture previewTex;
    private int previewN;
    private int lastFrame = -1;
    private int lastColor = -1;

    public LavaPanel() {
        currentColor = LavaColorManager.INSTANCE.isEnabled()
            ? LavaColorManager.INSTANCE.getColor() : LavaColorManager.DEFAULT_COLOR;
        for (int i = 0; i < swatchHover.length; i++) {
            swatchHover[i] = new Anim.Toggle(Theme.MS_HOVER, Anim.Ease.OUT_CUBIC);
        }
    }

    // --- rendering -------------------------------------------------------------

    public void render(GuiGraphicsExtractor ctx, Screen parent, int x, int top, int right, int bottom, int mouseX, int mouseY) {
        float left = x + PAD;
        float y = top;

        // Base colours, then the palette on its own line under a hairline.
        for (int c = 0; c < BASE.length; c++) {
            drawSwatch(ctx, c, left + c * (SWATCH + GAP), y, BASE[c], mouseX, mouseY);
        }
        float paletteY = y + SWATCH + 9;
        UiPanel.hairline(ctx, left, left + 10 * (SWATCH + GAP) - GAP, paletteY - 5);
        for (int i = 0; i < LavaColorManager.CUSTOM_SLOTS; i++) {
            int slot = LavaColorManager.INSTANCE.getCustomSlot(i);
            float sx = left + i * (SWATCH + GAP);
            if (slot == LavaColorManager.EMPTY) {
                drawEmptySlot(ctx, BASE.length + i, sx, paletteY, mouseX, mouseY);
            } else {
                drawSwatch(ctx, BASE.length + i, sx, paletteY, slot, mouseX, mouseY);
            }
            if (parent instanceof UiScreen screen && swatchRects[BASE.length + i] != null
                    && inside(swatchRects[BASE.length + i], mouseX, mouseY)) {
                screen.tooltip().offer("lava-slot" + i, slot == LavaColorManager.EMPTY
                        ? List.of("Empty slot", "Click to add a colour.")
                        : List.of(String.format("#%06X", slot), "Right-click to clear."));
            }
        }

        // Live preview on the right.
        float boxX = right - PAD - PREVIEW;
        float boxY = y;
        updatePreviewIfNeeded();
        if (previewTex != null) {
            ctx.blit(RenderPipelines.GUI_TEXTURED, PREVIEW_ID, Math.round(boxX), Math.round(boxY), 0f, 0f,
                    PREVIEW, PREVIEW, previewN, previewN, previewN, previewN);
        } else {
            Draw.rect(ctx, boxX, boxY, boxX + PREVIEW, boxY + PREVIEW, 0xFF000000 | currentColor);
        }
        Draw.outline(ctx, boxX, boxY, PREVIEW, PREVIEW, 1f, 1f, 0x33FFFFFF);
        boolean water = LavaColorManager.INSTANCE.isUseWaterTexture();
        String caption = String.format("#%06X", currentColor) + (water ? " · water texture" : " · lava texture");
        UiText.drawCentered(ctx, UiText.ellipsize(caption, UiText.Style.BODY, PREVIEW + 20), UiText.Style.BODY,
                boxX + PREVIEW / 2f, boxY + PREVIEW + 6, Theme.TEXT_2);

        // Actions: texture choice on the left, colour reset on the right of the swatches.
        float actionsY = paletteY + SWATCH + 14;
        texture.bounds(left, actionsY, texture.preferredWidth(), 16).render(ctx, mouseX, mouseY);
        float resetW = reset.preferredWidth();
        reset.bounds(left + 10 * (SWATCH + GAP) - GAP - resetW, actionsY, resetW, 16).render(ctx, mouseX, mouseY);
    }

    private void drawSwatch(GuiGraphicsExtractor ctx, int index, float x, float y, int rgb, int mouseX, int mouseY) {
        swatchRects[index] = new float[]{x, y, x + SWATCH, y + SWATCH};
        float hv = swatchHover[index].update(inside(swatchRects[index], mouseX, mouseY));
        float lift = hv;
        boolean selected = rgb == currentColor;
        if (selected) {
            Draw.outline(ctx, x - 3, y - 3 - lift, SWATCH + 6, SWATCH + 6, Theme.RADIUS_CONTROL + 2, 1.5f, Theme.CYAN);
        }
        Draw.roundRect(ctx, x, y - lift, SWATCH, SWATCH, Theme.RADIUS_CONTROL, 0xFF000000 | rgb);
        Draw.outline(ctx, x, y - lift, SWATCH, SWATCH, Theme.RADIUS_CONTROL, 1f, 0x2EFFFFFF, 0x1A000000);
    }

    private void drawEmptySlot(GuiGraphicsExtractor ctx, int index, float x, float y, int mouseX, int mouseY) {
        swatchRects[index] = new float[]{x, y, x + SWATCH, y + SWATCH};
        float hv = swatchHover[index].update(inside(swatchRects[index], mouseX, mouseY));
        Draw.roundRect(ctx, x, y, SWATCH, SWATCH, Theme.RADIUS_CONTROL, Draw.mix(Theme.INSET, Theme.INSET_HOVER, hv));
        Draw.outline(ctx, x, y, SWATCH, SWATCH, Theme.RADIUS_CONTROL, 1f, Draw.mix(Theme.LINE_STRONG, Theme.ACCENT, hv));
        Draw.plus(ctx, x + SWATCH / 2f, y + SWATCH / 2f, 6f, Draw.mix(Theme.TEXT_3, Theme.TEXT, hv));
    }

    // --- input -----------------------------------------------------------------

    public boolean mouseClicked(Screen parent, double mx, double my, int button) {
        if (texture.mouseClicked(mx, my, button)) return true;
        if (reset.mouseClicked(mx, my, button)) return true;

        // Base colours (left click).
        if (button == 0) {
            for (int c = 0; c < BASE.length; c++) {
                if (swatchRects[c] != null && inside(swatchRects[c], mx, my)) {
                    applyColor(BASE[c]);
                    return true;
                }
            }
        }
        // Palette slots: left picks (or adds, when empty), right clears.
        for (int i = 0; i < LavaColorManager.CUSTOM_SLOTS; i++) {
            float[] rect = swatchRects[BASE.length + i];
            if (rect == null || !inside(rect, mx, my)) continue;
            int slot = LavaColorManager.INSTANCE.getCustomSlot(i);
            if (button == 1) {
                LavaColorManager.INSTANCE.clearCustomSlot(i);
            } else if (slot == LavaColorManager.EMPTY) {
                int idx = i;
                Minecraft.getInstance().setScreen(new HexPromptScreen(parent, "#",
                    rgb -> LavaColorManager.INSTANCE.setCustomSlot(idx, rgb)));
            } else {
                applyColor(slot);
            }
            return true;
        }
        return false;
    }

    private void applyColor(int rgb) {
        currentColor = rgb & 0xFFFFFF;
        LavaColorManager.INSTANCE.setColor(currentColor);
    }

    private static boolean inside(float[] r, double mx, double my) {
        return mx >= r[0] && mx < r[2] && my >= r[1] && my < r[3];
    }

    // --- preview ---------------------------------------------------------------

    private void updatePreviewIfNeeded() {
        if (previewTex == null) {
            previewN = LavaColorManager.INSTANCE.getPreviewSize();
            if (previewN <= 0) return;
            previewImg = new NativeImage(previewN, previewN, false);
            previewTex = new DynamicTexture((java.util.function.Supplier<String>) () -> "lava panel preview", previewImg);
            Minecraft.getInstance().getTextureManager().register(PREVIEW_ID, previewTex);
            lastFrame = -1;
            lastColor = -1;
        }
        int frameCount = LavaColorManager.INSTANCE.getPreviewFrameCount();
        int frame = frameCount <= 1 ? 0 : (int) ((System.currentTimeMillis() / 120) % frameCount);
        if (frame != lastFrame || currentColor != lastColor) {
            LavaColorManager.INSTANCE.fillPreview(previewImg, currentColor, frame);
            previewTex.upload();
            lastFrame = frame;
            lastColor = currentColor;
        }
    }

    public void dispose() {
        if (previewTex != null) {
            Minecraft.getInstance().getTextureManager().release(PREVIEW_ID);
            previewTex = null;
            previewImg = null;
        }
    }
}
