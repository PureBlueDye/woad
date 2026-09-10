package com.pureblue.woad.core;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;

/** Shared constants and small helpers for the mod. */
public final class Woad {

    public static final String MOD_ID = "woad";
    public static final String NAME = "Woad";

    /** Accent color used across the UI (a soft, sober periwinkle blue), as 0xAARRGGBB. */
    public static final int ACCENT = 0xFF7AA2F7;

    /** Brand colors (24-bit RGB): orange brackets, light-blue name. */
    public static final int ORANGE = 0xFFA000;
    public static final int LIGHT_BLUE = 0x55C8FF;

    private Woad() {}

    /** The "[Woad]" chat prefix: orange brackets, light-blue name. */
    public static MutableComponent prefix() {
        return prefix(NAME);
    }

    /** A brand-coloured "[name] " prefix — orange brackets, light-blue label. */
    public static MutableComponent prefix(String label) {
        Style orange = Style.EMPTY.withColor(TextColor.fromRgb(ORANGE));
        Style lightBlue = Style.EMPTY.withColor(TextColor.fromRgb(LIGHT_BLUE)).withBold(true);
        return Component.literal("[").setStyle(orange)
                .append(Component.literal(label).setStyle(lightBlue))
                .append(Component.literal("] ").setStyle(orange));
    }

    /** Adds a "[Woad] ..." line to the client chat (display only, nothing is sent to the server). */
    public static void sendPrefixedMessage(Component body) {
        addClientMessage(prefix().append(body));
    }

    /** Adds an "[AI] ..." line to the client chat, in the same colours as the mod prefix. */
    public static void sendAiMessage(Component body) {
        addClientMessage(prefix("AI").append(body));
    }

    private static void addClientMessage(Component line) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui == null) return;
        mc.gui.getChat().addClientSystemMessage(line);
    }
}
