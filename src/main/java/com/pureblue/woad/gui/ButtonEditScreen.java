package com.pureblue.woad.gui;

import com.pureblue.woad.config.ConfigStore;
import com.pureblue.woad.features.InventoryButtonsFeature;
import com.pureblue.woad.features.InventoryButtonsFeature.CmdButton;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;

/** Per-button editor: the command it runs plus its inside (an item id or a label/number). */
public class ButtonEditScreen extends Screen {

    private static final int PANEL_W = 250;
    private static final int PANEL_H = 172;

    private final Screen parent;
    private final InventoryButtonsFeature feature;
    private final CmdButton button;

    private EditBox commandField;
    private EditBox itemField;
    private EditBox labelField;

    public ButtonEditScreen(Screen parent, InventoryButtonsFeature feature, CmdButton button) {
        super(Component.literal("Edit button"));
        this.parent = parent;
        this.feature = feature;
        this.button = button;
    }

    @Override
    protected void init() {
        int left = (this.width - PANEL_W) / 2;
        int top = (this.height - PANEL_H) / 2;
        int fw = PANEL_W - 32;

        commandField = new EditBox(this.font, left + 16, top + 34, fw, 18, Component.literal("command"));
        commandField.setMaxLength(256);
        commandField.setValue(button.command == null ? "" : button.command);
        this.addRenderableWidget(commandField);
        this.setInitialFocus(commandField);

        // Item field is a little narrower to leave room for the preview icon.
        itemField = new EditBox(this.font, left + 16, top + 72, fw - 22, 18, Component.literal("item"));
        itemField.setMaxLength(128);
        itemField.setValue(button.item == null ? "" : button.item);
        this.addRenderableWidget(itemField);

        labelField = new EditBox(this.font, left + 16, top + 110, fw, 18, Component.literal("label"));
        labelField.setMaxLength(16);
        labelField.setValue(button.label == null ? "" : button.label);
        this.addRenderableWidget(labelField);

        int btnW = (fw - 6) / 2;
        this.addRenderableWidget(Button.builder(Component.literal("OK"), b -> confirm())
            .bounds(left + 16, top + 140, btnW, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> onClose())
            .bounds(left + 16 + btnW + 6, top + 140, btnW, 20).build());
    }

    private void confirm() {
        button.command = commandField.getValue().trim();
        button.item = itemField.getValue().trim();
        button.label = labelField.getValue().trim();
        ConfigStore.save();
        onClose();
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        int left = (this.width - PANEL_W) / 2;
        int top = (this.height - PANEL_H) / 2;
        ctx.fill(0, 0, this.width, this.height, 0x80000000);
        ctx.fill(left, top, left + PANEL_W, top + PANEL_H, 0xF0101010);

        ctx.text(this.font, "Command", left + 16, top + 22, 0xFFB0B0B8, false);
        ctx.text(this.font, "Item id, or skull:<player / texture> (optional)", left + 16, top + 60, 0xFFB0B0B8, false);
        ctx.text(this.font, "Label / number (optional)", left + 16, top + 98, 0xFFB0B0B8, false);

        super.extractRenderState(ctx, mouseX, mouseY, delta);

        // Live item preview to the right of the item field.
        ItemStack preview = InvButtonRenderer.resolveItem(itemField.getValue());
        int px = left + 16 + (PANEL_W - 32) - 18;
        int py = top + 72;
        ctx.fill(px, py, px + 18, py + 18, 0xFF202028);
        if (!preview.isEmpty()) {
            ctx.item(preview, px + 1, py + 1);
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        // Dim drawn in render().
    }
}
