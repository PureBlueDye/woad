package com.pureblue.woad.gui;

import com.pureblue.woad.ui.UiDialog;
import net.minecraft.client.gui.screens.Screen;

import java.util.function.Consumer;

/** Asks for one line of text — a setting's value, a command — and hands it back on OK or Enter. */
public class TextPromptScreen extends UiDialog {

    private final Consumer<String> onConfirm;

    public TextPromptScreen(Screen parent, String title, String initial, Consumer<String> onConfirm) {
        this(parent, title, null, initial, onConfirm);
    }

    /** @param description a line under the title explaining what the value is for, or null */
    public TextPromptScreen(Screen parent, String title, String description, String initial, Consumer<String> onConfirm) {
        super(parent, title, description, initial, 256);
        this.onConfirm = onConfirm;
    }

    @Override
    protected void confirm(String value) {
        onConfirm.accept(value);
    }
}
