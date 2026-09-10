package com.pureblue.woad.gui;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;

/**
 * Renders the {@code &}-style colour codes players type, so a text field shows the result while it
 * is being written instead of a row of raw codes.
 *
 * <p>The codes themselves stay visible and dimmed — they have to remain editable — while the text
 * after each one takes the colour it will actually have on the item.
 */
public final class LegacyColors {

    /** Colour used for the code characters themselves, so they read as markup, not content. */
    private static final int MARKUP = 0xFF5A5A66;

    private LegacyColors() {}

    /**
     * Builds a drawable sequence for {@code text}, starting from the style already in force.
     *
     * @param carried the style inherited from the characters before this fragment
     */
    public static FormattedCharSequence render(String text, Style carried) {
        List<FormattedCharSequence> parts = new ArrayList<>();
        Style style = carried;
        StringBuilder run = new StringBuilder();

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            ChatFormatting code = c == '&' && i + 1 < text.length()
                    ? ChatFormatting.getByCode(text.charAt(i + 1)) : null;
            if (code == null) {
                run.append(c);
                continue;
            }
            // Flush what came before, then draw "&x" greyed out and switch style for what follows.
            if (!run.isEmpty()) {
                parts.add(FormattedCharSequence.forward(run.toString(), style));
                run.setLength(0);
            }
            parts.add(FormattedCharSequence.forward(text.substring(i, i + 2),
                    Style.EMPTY.withColor(MARKUP)));
            style = apply(style, code);
            i++; // the code letter was consumed with the ampersand
        }
        if (!run.isEmpty()) {
            parts.add(FormattedCharSequence.forward(run.toString(), style));
        }
        return FormattedCharSequence.composite(parts);
    }

    /** The style in force at {@code index}, by replaying the codes before it. */
    public static Style styleAt(String text, int index) {
        Style style = Style.EMPTY;
        int end = Math.min(index, text.length());
        for (int i = 0; i < end; i++) {
            if (text.charAt(i) != '&' || i + 1 >= text.length()) continue;
            ChatFormatting code = ChatFormatting.getByCode(text.charAt(i + 1));
            if (code != null) {
                style = apply(style, code);
                i++;
            }
        }
        return style;
    }

    /** A colour replaces the previous one and clears formats, exactly as the game does. */
    private static Style apply(Style style, ChatFormatting code) {
        if (code == ChatFormatting.RESET) return Style.EMPTY;
        if (code.isFormat()) return style.applyFormat(code);
        return Style.EMPTY.applyFormat(code);
    }
}
