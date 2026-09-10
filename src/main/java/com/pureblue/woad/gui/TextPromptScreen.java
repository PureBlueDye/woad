package com.pureblue.woad.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

/** A small popup with a title, a text field and OK/Cancel; calls {@code onConfirm} with the text. */
public class TextPromptScreen extends Screen {

    private static final int PANEL_W = 240;
    private static final int PANEL_H = 100;

    private final Screen parent;
    private final String title;
    private final String initial;
    private final Consumer<String> onConfirm;
    private EditBox field;

    public TextPromptScreen(Screen parent, String title, String initial, Consumer<String> onConfirm) {
        super(Component.literal(title));
        this.parent = parent;
        this.title = title;
        this.initial = initial;
        this.onConfirm = onConfirm;
    }

    @Override
    protected void init() {
        int left = (this.width - PANEL_W) / 2;
        int top = (this.height - PANEL_H) / 2;

        this.field = new EditBox(this.font, left + 16, top + 38, PANEL_W - 32, 18,
            Component.literal("command"));
        this.field.setMaxLength(256);
        this.field.setValue(initial);
        this.setInitialFocus(this.field);
        this.addRenderableWidget(this.field);

        int btnW = (PANEL_W - 32 - 6) / 2;
        this.addRenderableWidget(Button.builder(Component.literal("OK"), b -> confirm())
            .bounds(left + 16, top + 64, btnW, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> onClose())
            .bounds(left + 16 + btnW + 6, top + 64, btnW, 20).build());
    }

    private void confirm() {
        onConfirm.accept(this.field.getValue().trim());
        onClose();
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent input) {
        // Enter confirms.
        if (input.key() == 257 || input.key() == 335) {
            confirm();
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        int left = (this.width - PANEL_W) / 2;
        int top = (this.height - PANEL_H) / 2;
        ctx.fill(0, 0, this.width, this.height, 0x80000000);
        ctx.fill(left, top, left + PANEL_W, top + PANEL_H, 0xF0101010);
        ctx.centeredText(this.font, Component.literal(title),
            this.width / 2, top + 14, 0xFFFFFFFF);
        super.extractRenderState(ctx, mouseX, mouseY, delta);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        // Dim drawn in render().
    }
}
