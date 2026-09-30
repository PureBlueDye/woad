package com.pureblue.woad.gui;

import com.pureblue.woad.config.ConfigStore;
import com.pureblue.woad.features.InventoryButtonsFeature;
import com.pureblue.woad.features.InventoryButtonsFeature.CmdButton;
import com.pureblue.woad.ui.Anim;
import com.pureblue.woad.ui.Draw;
import com.pureblue.woad.ui.Theme;
import com.pureblue.woad.ui.UiPanel;
import com.pureblue.woad.ui.UiScreen;
import com.pureblue.woad.ui.UiText;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Placement editor for the inventory command buttons. Shows the inventory layout (its slots are
 * off-limits) with the placeable grid around and between them. Left-click a free cell to add a
 * button, left-click a button to remove it, right-click a button to set its command.
 *
 * <p>Placed buttons are drawn exactly as they will appear in the real inventory, in the style the
 * player chose — this screen previews placement, not a redesign of the buttons themselves.
 */
public class InventoryButtonsScreen extends UiScreen {

    private static final int BG_W = 176;
    private static final int BG_H = 166;

    private final Screen parent;
    private final InventoryButtonsFeature feature;
    private final Map<Integer, Anim.Toggle> cellHover = new HashMap<>();

    public InventoryButtonsScreen(Screen parent, InventoryButtonsFeature feature) {
        super(Component.literal("Inventory Buttons"));
        this.parent = parent;
        this.feature = feature;
    }

    private int gx() {
        return (this.width - BG_W) / 2;
    }

    private int gy() {
        return (this.height - BG_H) / 2 + 8;
    }

    private Anim.Toggle hoverOf(int col, int row) {
        return cellHover.computeIfAbsent(col * 1000 + row, k -> new Anim.Toggle(Theme.MS_HOVER, Anim.Ease.OUT_CUBIC));
    }

    @Override
    protected void renderContent(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        int gx = gx();
        int gy = gy();

        // Instructions, in a pill at the top.
        String hint = "Left-click: add or remove  ·  Right-click a button: edit  ·  Esc: save";
        float hintW = UiText.width(hint, UiText.Style.LABEL) + 20;
        float hx = (this.width - hintW) / 2f;
        float hy = Math.max(6, InventoryGrid.cellY(gy, InventoryGrid.ROW_MIN) - 34);
        Draw.roundRect(ctx, hx, hy, hintW, 18, 9f, Theme.TOOLTIP_BG);
        Draw.outline(ctx, hx, hy, hintW, 18, 9f, 1f, Theme.LINE_STRONG);
        UiText.draw(ctx, hint, UiText.Style.LABEL, hx + 10, UiText.centerY(UiText.Style.LABEL, hy, 18), Theme.TEXT);

        // The whole placeable area on a glass panel, the inventory itself as a recessed card.
        float areaX = InventoryGrid.cellX(gx, InventoryGrid.COL_MIN) - 7;
        float areaY = InventoryGrid.cellY(gy, InventoryGrid.ROW_MIN) - 7;
        float areaW = InventoryGrid.cellX(gx, InventoryGrid.COL_MAX) + InventoryGrid.BTN + 7 - areaX;
        float areaH = InventoryGrid.cellY(gy, InventoryGrid.ROW_MAX) + InventoryGrid.BTN + 7 - areaY;
        UiPanel.panel(ctx, areaX, areaY, areaW, areaH);
        Draw.roundRect(ctx, gx - 3, gy - 3, BG_W + 6, BG_H + 6, Theme.RADIUS_CARD, Draw.withAlpha(Theme.INSET, 0.85f));
        Draw.outline(ctx, gx - 3, gy - 3, BG_W + 6, BG_H + 6, Theme.RADIUS_CARD, 1f, Theme.LINE_STRONG);
        for (int[] s : InventoryGrid.slots()) {
            // The inventory's own slots: shown for orientation, visibly unavailable — flat and
            // without an edge, unlike the outlined cells a button can go in.
            float sx = gx + s[0];
            float sy = gy + s[1];
            Draw.roundRect(ctx, sx, sy, 16, 16, Theme.RADIUS_SLOT, 0x8C070C18);
        }

        // The grid: free cells, and the buttons already placed.
        for (int row = InventoryGrid.ROW_MIN; row <= InventoryGrid.ROW_MAX; row++) {
            for (int col = InventoryGrid.COL_MIN; col <= InventoryGrid.COL_MAX; col++) {
                int cx = InventoryGrid.cellX(gx, col);
                int cy = InventoryGrid.cellY(gy, row);
                boolean over = mouseX >= cx && mouseX < cx + 16 && mouseY >= cy && mouseY < cy + 16;
                CmdButton b = feature.buttonAt(col, row);

                if (b != null) {
                    float h = hoverOf(col, row).update(over);
                    if (h > 0.01f) Draw.shadow(ctx, cx, cy, 16, 16, Theme.RADIUS_SLOT, 4f, 0f, Draw.withAlpha(Theme.GLOW, h));
                    InvButtonRenderer.draw(ctx, this.font, cx, cy, over,
                        feature.getStyle(), feature.getBorderColor(), b);
                    if (over) {
                        tooltip.offer("cell" + col + ":" + row, List.of(
                                !b.command.isBlank() ? "/" + b.command.replaceFirst("^/", "") : "Empty button",
                                "Left-click to remove, right-click to edit."));
                    }
                } else if (!InventoryGrid.isSlotCell(col, row)) {
                    float h = hoverOf(col, row).update(over);
                    Draw.roundRect(ctx, cx, cy, 16, 16, Theme.RADIUS_SLOT, Draw.withAlpha(Theme.FILL, 0.18f * h));
                    Draw.outline(ctx, cx, cy, 16, 16, Theme.RADIUS_SLOT, 1f,
                            Draw.mix(Draw.withAlpha(Theme.LINE_STRONG, 0.5f), Theme.ACCENT, h));
                    if (h > 0.01f) Draw.plus(ctx, cx + 8, cy + 8, 6f, Draw.withAlpha(Theme.CYAN, h));
                }
            }
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        int mx = (int) click.x();
        int my = (int) click.y();
        int gx = gx();
        int gy = gy();

        for (int row = InventoryGrid.ROW_MIN; row <= InventoryGrid.ROW_MAX; row++) {
            for (int col = InventoryGrid.COL_MIN; col <= InventoryGrid.COL_MAX; col++) {
                int cx = InventoryGrid.cellX(gx, col);
                int cy = InventoryGrid.cellY(gy, row);
                if (mx >= cx && mx < cx + 16 && my >= cy && my < cy + 16) {
                    CmdButton b = feature.buttonAt(col, row);
                    if (b != null) {
                        if (click.button() == 1) {
                            feature.editButton(this, b);
                        } else {
                            feature.removeButton(b);
                        }
                    } else if (!InventoryGrid.isSlotCell(col, row) && click.button() == 0) {
                        feature.addButton(col, row);
                    }
                    return true;
                }
            }
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public void onClose() {
        ConfigStore.save();
        this.minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
