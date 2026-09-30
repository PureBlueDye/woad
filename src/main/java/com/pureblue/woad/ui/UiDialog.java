package com.pureblue.woad.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * A small dialog asking for one value: a title, an optional line of explanation, a text field, and
 * Cancel / OK. Enter confirms, Escape cancels; either way the previous screen comes back.
 *
 * <p>Subclasses decide what OK does ({@link #confirm}) and may draw something beside the field
 * (a colour swatch, say) by reserving room with {@link #fieldAccessoryWidth}.
 */
public abstract class UiDialog extends UiScreen {

    private static final int WIDTH = 210;
    private static final int PAD = 14;

    protected final Screen parent;
    private final String heading;
    private final String description;
    protected final UiTextField field;
    private final UiButton cancel = add(new UiButton("Cancel", UiButton.Variant.GHOST, this::onClose));
    private final UiButton ok = add(new UiButton("OK", UiButton.Variant.PRIMARY, this::confirmAndClose));

    protected UiDialog(Screen parent, String heading, String description, String initial, int maxLength) {
        super(Component.literal(heading));
        this.parent = parent;
        this.heading = heading;
        this.description = description == null ? "" : description;
        this.field = add(new UiTextField(initial, maxLength));
    }

    /** Applies the value. The dialog closes right after. */
    protected abstract void confirm(String value);

    /** Width kept free to the left of the field, for {@link #drawFieldAccessory}. */
    protected int fieldAccessoryWidth() {
        return 0;
    }

    protected void drawFieldAccessory(GuiGraphicsExtractor ctx, float x, float y, float size) {}

    @Override
    protected void init() {
        addWidget(field.box());
        setInitialFocus(field.box());
    }

    private void confirmAndClose() {
        confirm(field.value().trim());
        onClose();
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) this.minecraft.setScreen(parent);
    }

    @Override
    public boolean keyPressed(KeyEvent input) {
        if (input.key() == GLFW.GLFW_KEY_ENTER || input.key() == GLFW.GLFW_KEY_KP_ENTER) {
            confirmAndClose();
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    protected void renderContent(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        int textW = WIDTH - PAD * 2;
        int descH = description.isBlank() ? 0 : UiText.wrappedHeight(description, UiText.Style.BODY, textW);
        float h = PAD + 13 + (descH > 0 ? descH + 3 : 4) + Theme.FIELD_H + 12 + Theme.BUTTON_H_SMALL + PAD;
        float x = Math.round((this.width - WIDTH) / 2f);
        float y = Math.round((this.height - h) / 2f);
        UiPanel.panel(ctx, x, y, WIDTH, h);

        float cy = y + PAD;
        UiText.draw(ctx, heading, UiText.Style.HEADING, x + PAD, cy, Theme.TEXT);
        cy += 13;
        if (descH > 0) {
            UiText.drawWrapped(ctx, description, UiText.Style.BODY, x + PAD, cy, textW, Theme.TEXT_2);
            cy += descH + 3;
        } else {
            cy += 4;
        }

        int accessory = fieldAccessoryWidth();
        if (accessory > 0) {
            drawFieldAccessory(ctx, x + PAD, cy, Theme.FIELD_H);
        }
        float fieldX = x + PAD + (accessory > 0 ? accessory + 6 : 0);
        field.bounds(fieldX, cy, x + WIDTH - PAD - fieldX, Theme.FIELD_H).render(ctx, mouseX, mouseY);
        cy += Theme.FIELD_H + 12;

        float okW = Math.max(46, ok.preferredWidth());
        float cancelW = cancel.preferredWidth();
        ok.bounds(x + WIDTH - PAD - okW, cy, okW, Theme.BUTTON_H_SMALL).render(ctx, mouseX, mouseY);
        cancel.bounds(x + WIDTH - PAD - okW - 4 - cancelW, cy, cancelW, Theme.BUTTON_H_SMALL).render(ctx, mouseX, mouseY);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
