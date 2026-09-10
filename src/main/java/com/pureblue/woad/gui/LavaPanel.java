package com.pureblue.woad.gui;

import com.pureblue.woad.lava.LavaColorManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * The Custom Lava editor, drawn directly inside the Woad menu's content panel (no separate
 * screen). 5 base colours, 10 user palette slots (empty = "+", right-click clears), a live lava
 * preview, a Lava/Water texture toggle, a hex input (popup) and Apply/Reset.
 */
public class LavaPanel {

    private static final int[] BASE = {0xFF0000, 0xFF7F00, 0xFFFF00, 0x00FF00, 0x0000FF};
    private static final int COLS = 5;
    private static final int CELL = 22;
    private static final int PAD = 14;
    private static final Identifier PREVIEW_ID = Identifier.fromNamespaceAndPath("woad", "lava_panel_preview");

    // Layout captured during render, used by mouseClicked.
    private int gridX;
    private int gridY;
    private final int[] texBtn = new int[4];
    private final int[] hexBtn = new int[4];
    private final int[] applyBtn = new int[4];
    private final int[] resetBtn = new int[4];

    private int currentColor;

    // Live preview texture.
    private NativeImage previewImg;
    private DynamicTexture previewTex;
    private int previewN;
    private int lastFrame = -1;
    private int lastColor = -1;

    public LavaPanel() {
        currentColor = LavaColorManager.INSTANCE.isEnabled()
            ? LavaColorManager.INSTANCE.getColor() : LavaColorManager.DEFAULT_COLOR;
    }

    // --- rendering -------------------------------------------------------------

    public void render(GuiGraphicsExtractor ctx, int x, int top, int right, int bottom, int mouseX, int mouseY) {
        Font tr = Minecraft.getInstance().font;
        gridX = x + PAD;
        gridY = top;
        int gridW = COLS * CELL;

        // Base colours.
        for (int c = 0; c < BASE.length; c++) {
            drawCell(ctx, gridX + c * CELL, gridY, BASE[c], BASE[c] == currentColor);
        }
        // Custom slots.
        for (int i = 0; i < LavaColorManager.CUSTOM_SLOTS; i++) {
            int col = i % COLS;
            int row = 1 + i / COLS;
            int cx = gridX + col * CELL;
            int cy = gridY + row * CELL;
            int slot = LavaColorManager.INSTANCE.getCustomSlot(i);
            if (slot == LavaColorManager.EMPTY) {
                drawPlus(ctx, tr, cx, cy);
            } else {
                drawCell(ctx, cx, cy, slot, slot == currentColor);
            }
        }

        // Live lava preview to the right of the grid. Square (the lava frame is square) so it
        // isn't stretched; sized to the grid height.
        int boxH = 3 * CELL;
        int boxW = boxH;
        int boxX = gridX + gridW + 12;
        int boxY = gridY;
        updatePreviewIfNeeded();
        if (previewTex != null) {
            ctx.blit(RenderPipelines.GUI_TEXTURED, PREVIEW_ID,
                boxX, boxY, 0f, 0f, boxW, boxH, previewN, previewN, previewN, previewN);
        } else {
            ctx.fill(boxX, boxY, boxX + boxW, boxY + boxH, 0xFF000000 | currentColor);
        }
        border(ctx, boxX, boxY, boxW, boxH, 0xFF202020);

        // Buttons under the grid (two rows of two).
        int by = gridY + 3 * CELL + 8;
        int fullW = right - PAD - gridX;
        int halfW = (fullW - 6) / 2;
        boolean water = LavaColorManager.INSTANCE.isUseWaterTexture();
        button(ctx, tr, texBtn, gridX, by, halfW, "Texture: " + (water ? "Water" : "Lava"), mouseX, mouseY);
        button(ctx, tr, hexBtn, gridX + halfW + 6, by, halfW, "Hex...", mouseX, mouseY);
        button(ctx, tr, applyBtn, gridX, by + 24, halfW, "Apply", mouseX, mouseY);
        button(ctx, tr, resetBtn, gridX + halfW + 6, by + 24, halfW, "Reset", mouseX, mouseY);
    }

    // --- input -----------------------------------------------------------------

    public boolean mouseClicked(Screen parent, double mx, double my, int button) {
        boolean right = button == 1;

        // Base colours (left click).
        if (button == 0) {
            for (int c = 0; c < BASE.length; c++) {
                if (inCell(mx, my, gridX + c * CELL, gridY)) {
                    applyColor(BASE[c]);
                    return true;
                }
            }
        }
        // Custom slots (left = pick/add, right = clear).
        for (int i = 0; i < LavaColorManager.CUSTOM_SLOTS; i++) {
            int col = i % COLS;
            int row = 1 + i / COLS;
            if (inCell(mx, my, gridX + col * CELL, gridY + row * CELL)) {
                int slot = LavaColorManager.INSTANCE.getCustomSlot(i);
                if (right) {
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
        }
        // Buttons (left click).
        if (button == 0) {
            if (in(texBtn, mx, my)) {
                LavaColorManager.INSTANCE.setUseWaterTexture(!LavaColorManager.INSTANCE.isUseWaterTexture());
                return true;
            }
            if (in(hexBtn, mx, my)) {
                Minecraft.getInstance().setScreen(new HexPromptScreen(parent,
                    String.format("#%06X", currentColor), this::applyColor));
                return true;
            }
            if (in(applyBtn, mx, my)) {
                LavaColorManager.INSTANCE.commitTexture();
                Minecraft.getInstance().reloadResourcePacks();
                return true;
            }
            if (in(resetBtn, mx, my)) {
                LavaColorManager.INSTANCE.reset();
                currentColor = LavaColorManager.DEFAULT_COLOR;
                Minecraft.getInstance().reloadResourcePacks();
                return true;
            }
        }
        return false;
    }

    private void applyColor(int rgb) {
        currentColor = rgb & 0xFFFFFF;
        LavaColorManager.INSTANCE.setColor(currentColor);
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

    // --- drawing helpers -------------------------------------------------------

    private void drawCell(GuiGraphicsExtractor ctx, int x, int y, int color, boolean selected) {
        int s = CELL - 2;
        ctx.fill(x, y, x + s, y + s, 0xFF000000 | color);
        border(ctx, x, y, s, s, selected ? 0xFFFFFFFF : 0xFF202020);
    }

    private void drawPlus(GuiGraphicsExtractor ctx, Font tr, int x, int y) {
        int s = CELL - 2;
        ctx.fill(x, y, x + s, y + s, 0xFF2A2A2A);
        border(ctx, x, y, s, s, 0xFF202020);
        ctx.centeredText(tr, Component.literal("+"), x + s / 2, y + s / 2 - 4, 0xFFAAAAAA);
    }

    private void button(GuiGraphicsExtractor ctx, Font tr, int[] rect, int x, int y, int w, String label, int mouseX, int mouseY) {
        int h = 18;
        boolean hovered = mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
        ctx.fill(x, y, x + w, y + h, hovered ? 0xFF3A3A44 : 0xFF26262C);
        border(ctx, x, y, w, h, 0xFF45454F);
        ctx.centeredText(tr, Component.literal(label), x + w / 2, y + 5, 0xFFE8E8EC);
        rect[0] = x; rect[1] = y; rect[2] = x + w; rect[3] = y + h;
    }

    private static void border(GuiGraphicsExtractor ctx, int x, int y, int w, int h, int color) {
        ctx.fill(x, y, x + w, y + 1, color);
        ctx.fill(x, y + h - 1, x + w, y + h, color);
        ctx.fill(x, y, x + 1, y + h, color);
        ctx.fill(x + w - 1, y, x + w, y + h, color);
    }

    private static boolean inCell(double mx, double my, int cx, int cy) {
        return mx >= cx && mx < cx + CELL - 2 && my >= cy && my < cy + CELL - 2;
    }

    private static boolean in(int[] r, double mx, double my) {
        return mx >= r[0] && mx <= r[2] && my >= r[1] && my <= r[3];
    }
}
