package com.pureblue.woad.gui;

import com.pureblue.woad.ui.Draw;
import com.pureblue.woad.ui.Theme;
import com.pureblue.woad.ui.UiDialog;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;

import java.util.function.IntConsumer;

/**
 * Asks for a hex colour; calls {@code onConfirm} with the parsed 0xRRGGBB value.
 *
 * <p>A swatch beside the field shows the colour as it is typed, and the field turns red while the
 * text is not a valid colour. OK still closes either way, and only a valid colour is applied —
 * as before.
 */
public class HexPromptScreen extends UiDialog {

    private final IntConsumer onConfirm;

    public HexPromptScreen(Screen parent, String initialHex, IntConsumer onConfirm) {
        super(parent, "Hex color", null, initialHex, 7);
        this.onConfirm = onConfirm;
    }

    @Override
    protected void confirm(String value) {
        Integer rgb = parseHex(value);
        if (rgb != null) {
            onConfirm.accept(rgb);
        }
    }

    @Override
    protected int fieldAccessoryWidth() {
        return Theme.FIELD_H;
    }

    @Override
    protected void drawFieldAccessory(GuiGraphicsExtractor ctx, float x, float y, float size) {
        Integer rgb = parseHex(field.value());
        // Half-typed values ("#", "#FF8") are not errors yet; a full-length wrong one is.
        String typed = field.value().trim();
        field.invalid(rgb == null && typed.replace("#", "").length() >= 6);
        if (rgb != null) {
            Draw.roundRect(ctx, x, y, size, size, Theme.RADIUS_CONTROL, 0xFF000000 | rgb);
        } else {
            Draw.roundRect(ctx, x, y, size, size, Theme.RADIUS_CONTROL, Theme.INSET);
            Draw.line(ctx, x + 4, y + size - 4, x + size - 4, y + 4, 1.2f, Theme.TEXT_3);
        }
        Draw.outline(ctx, x, y, size, size, Theme.RADIUS_CONTROL, 1f, 0x2EFFFFFF);
    }

    static Integer parseHex(String text) {
        String s = text.trim();
        if (s.startsWith("#")) {
            s = s.substring(1);
        }
        if (s.length() != 6) {
            return null;
        }
        try {
            return Integer.parseInt(s, 16);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
