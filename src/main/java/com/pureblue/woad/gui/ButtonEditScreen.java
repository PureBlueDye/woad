package com.pureblue.woad.gui;

import com.pureblue.woad.config.ConfigStore;
import com.pureblue.woad.features.InventoryButtonsFeature;
import com.pureblue.woad.features.InventoryButtonsFeature.CmdButton;
import com.pureblue.woad.ui.Theme;
import com.pureblue.woad.ui.UiButton;
import com.pureblue.woad.ui.UiPanel;
import com.pureblue.woad.ui.UiScreen;
import com.pureblue.woad.ui.UiText;
import com.pureblue.woad.ui.UiTextField;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

/** Per-button editor: the command it runs plus its inside (an item id or a label/number). */
public class ButtonEditScreen extends UiScreen {

    private static final int PANEL_W = 250;
    private static final int PAD = 14;

    private final Screen parent;
    private final CmdButton button;

    private final UiTextField commandField;
    private final UiTextField itemField;
    private final UiTextField labelField;
    private final UiButton cancel = add(new UiButton("Cancel", UiButton.Variant.GHOST, this::onClose));
    private final UiButton ok = add(new UiButton("OK", UiButton.Variant.PRIMARY, this::confirm));

    public ButtonEditScreen(Screen parent, InventoryButtonsFeature feature, CmdButton button) {
        super(Component.literal("Edit button"));
        this.parent = parent;
        this.button = button;
        commandField = add(new UiTextField(button.command == null ? "" : button.command, 256).placeholder("warp dungeon_hub"));
        itemField = add(new UiTextField(button.item == null ? "" : button.item, 128).placeholder("diamond_sword"));
        labelField = add(new UiTextField(button.label == null ? "" : button.label, 16));
    }

    @Override
    protected void init() {
        addWidget(commandField.box());
        addWidget(itemField.box());
        addWidget(labelField.box());
        setInitialFocus(commandField.box());
    }

    private void confirm() {
        button.command = commandField.value().trim();
        button.item = itemField.value().trim();
        button.label = labelField.value().trim();
        ConfigStore.save();
        onClose();
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) this.minecraft.setScreen(parent);
    }

    @Override
    public boolean keyPressed(KeyEvent input) {
        if (input.key() == GLFW.GLFW_KEY_ENTER || input.key() == GLFW.GLFW_KEY_KP_ENTER) {
            confirm();
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    protected void renderContent(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        float fieldW = PANEL_W - PAD * 2;
        float h = PAD + 13 + 3 * (11 + Theme.FIELD_H + 8) + 4 + Theme.BUTTON_H_SMALL + PAD;
        float x = Math.round((this.width - PANEL_W) / 2f);
        float y = Math.round((this.height - h) / 2f);
        UiPanel.panel(ctx, x, y, PANEL_W, h);

        float cy = y + PAD;
        UiText.draw(ctx, "Edit button", UiText.Style.HEADING, x + PAD, cy, Theme.TEXT);
        cy += 17;

        label(ctx, "Command", x + PAD, cy);
        commandField.bounds(x + PAD, cy + 9, fieldW, Theme.FIELD_H).render(ctx, mouseX, mouseY);
        cy += 11 + Theme.FIELD_H + 8;

        // The item field leaves room for a live preview of what the button will show.
        label(ctx, "Item id, or skull:<player / texture> (optional)", x + PAD, cy);
        float slot = Theme.FIELD_H;
        itemField.bounds(x + PAD, cy + 9, fieldW - slot - 6, Theme.FIELD_H).render(ctx, mouseX, mouseY);
        float sx = x + PAD + fieldW - slot;
        float sy = cy + 9;
        ItemStack preview = InvButtonRenderer.resolveItem(itemField.value());
        UiPanel.slot(ctx, sx, sy, slot, 0f, !preview.isEmpty());
        if (!preview.isEmpty()) ctx.item(preview, Math.round(sx + 1), Math.round(sy + 1));
        cy += 11 + Theme.FIELD_H + 8;

        label(ctx, "Label / number (optional)", x + PAD, cy);
        labelField.bounds(x + PAD, cy + 9, fieldW, Theme.FIELD_H).render(ctx, mouseX, mouseY);
        cy += 11 + Theme.FIELD_H + 12;

        float okW = Math.max(46, ok.preferredWidth());
        float cancelW = cancel.preferredWidth();
        ok.bounds(x + PANEL_W - PAD - okW, cy, okW, Theme.BUTTON_H_SMALL).render(ctx, mouseX, mouseY);
        cancel.bounds(x + PANEL_W - PAD - okW - 4 - cancelW, cy, cancelW, Theme.BUTTON_H_SMALL).render(ctx, mouseX, mouseY);
    }

    private static void label(GuiGraphicsExtractor ctx, String text, float x, float y) {
        UiText.draw(ctx, text, UiText.Style.LABEL, x, y, Theme.TEXT_2);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
