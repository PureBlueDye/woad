package com.pureblue.woad.gui;

import com.pureblue.woad.config.ConfigStore;
import com.pureblue.woad.core.Woad;
import com.pureblue.woad.features.InventoryButtonsFeature;
import com.pureblue.woad.features.InventoryButtonsFeature.CmdButton;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Placement editor for the inventory command buttons. Shows the inventory layout (slots are gray
 * and off-limits) with the placeable grid around and between them. Left-click a free cell to add a
 * button, left-click a button to remove it, right-click a button to set its command.
 */
public class InventoryButtonsScreen extends Screen {

    private static final int BG_W = 176;
    private static final int BG_H = 166;
    private static final int SLOT_COLOR = 0xFF3A3A44;
    private static final int SLOT_BORDER = 0xFF14141A;

    private final Screen parent;
    private final InventoryButtonsFeature feature;

    public InventoryButtonsScreen(Screen parent, InventoryButtonsFeature feature) {
        super(Component.literal("Inventory Buttons"));
        this.parent = parent;
        this.feature = feature;
    }

    private int gx() {
        return (this.width - BG_W) / 2;
    }

    private int gy() {
        return (this.height - BG_H) / 2;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        super.extractRenderState(ctx, mouseX, mouseY, delta);
        ctx.fill(0, 0, this.width, this.height, 0x90000000);

        String hint = "Left-click: add / remove   •   Right-click a button: edit (command / item / label)   •   Esc: save";
        ctx.text(this.font, hint,
            this.width / 2 - this.font.width(hint) / 2, 16, 0xFFCCCCCC, true);

        int gx = gx();
        int gy = gy();

        // Inventory background representation + slots (forbidden zones).
        ctx.fill(gx - 4, gy - 4, gx + BG_W + 4, gy + BG_H + 4, 0xF01A1A1F);
        for (int[] s : com.pureblue.woad.gui.InventoryGrid.slots()) {
            int sx = gx + s[0];
            int sy = gy + s[1];
            ctx.fill(sx, sy, sx + 16, sy + 16, SLOT_COLOR);
            border(ctx, sx, sy, 16, 16, SLOT_BORDER);
        }

        // Grid: placeable outlines + placed buttons.
        String tooltip = null;
        for (int row = InventoryGrid.ROW_MIN; row <= InventoryGrid.ROW_MAX; row++) {
            for (int col = InventoryGrid.COL_MIN; col <= InventoryGrid.COL_MAX; col++) {
                int cx = InventoryGrid.cellX(gx, col);
                int cy = InventoryGrid.cellY(gy, row);
                boolean hover = mouseX >= cx && mouseX < cx + 16 && mouseY >= cy && mouseY < cy + 16;
                CmdButton b = feature.buttonAt(col, row);

                if (b != null) {
                    InvButtonRenderer.draw(ctx, this.font, cx, cy, hover,
                        feature.getStyle(), feature.getBorderColor(), b);
                    if (hover) tooltip = !b.command.isBlank() ? b.command : "Empty — right-click to edit";
                } else if (!InventoryGrid.isSlotCell(col, row)) {
                    border(ctx, cx, cy, 16, 16, hover ? 0x66FFFFFF : 0x1AFFFFFF);
                }
            }
        }
        if (tooltip != null) {
            ctx.setTooltipForNextFrame(Minecraft.getInstance().font, Component.literal(tooltip), mouseX, mouseY);
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

    private static void border(GuiGraphicsExtractor ctx, int x, int y, int w, int h, int color) {
        ctx.fill(x, y, x + w, y + 1, color);
        ctx.fill(x, y + h - 1, x + w, y + h, color);
        ctx.fill(x, y, x + 1, y + h, color);
        ctx.fill(x + w - 1, y, x + w, y + h, color);
    }
}
