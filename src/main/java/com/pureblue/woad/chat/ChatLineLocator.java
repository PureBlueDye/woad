package com.pureblue.woad.chat;

import com.pureblue.woad.mixin.ChatHudAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.util.Mth;

import java.util.List;

/**
 * Finds the full chat message under the mouse, in the open chat screen.
 *
 * <p>The geometry mirrors the chat renderer: everything is drawn in a space scaled by the chat
 * scale and shifted right by 4, with line {@code y} (0 = bottom-most) occupying the band
 * {@code [k - (y+1)*n, k - y*n)} where {@code k = floor((windowHeight - 40) / scale)} and {@code n}
 * is the line height.
 *
 * <p>A wrapped message spans several display lines, but each one keeps a reference to the message
 * it came from, so the untruncated text is one hop away.
 */
public final class ChatLineLocator {

    /** Vanilla's offset of the chat from the bottom of the screen. */
    private static final int OFFSET_FROM_BOTTOM = 40;

    private ChatLineLocator() {}

    /**
     * @return the plain text of the message under {@code (mouseX, mouseY)}, or {@code null} when the
     *         mouse is not over a chat line
     */
    public static String textAt(double mouseX, double mouseY) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui == null) return null;

        ChatComponent hud = mc.gui.getChat();
        if (!hud.isChatFocused()) return null;

        ChatHudAccessor access = (ChatHudAccessor) hud;
        List<GuiMessage.Line> visible = access.woad$visibleMessages();
        if (visible.isEmpty()) return null;

        double scale = access.woad$getChatScale();
        if (scale <= 0) return null;
        int lineHeight = access.woad$getLineHeight();
        if (lineHeight <= 0) return null;

        // Screen -> chat space (undo the scale, then the 4px shift applied to the pose).
        double chatX = mouseX / scale - 4.0;
        double chatY = mouseY / scale;

        int width = Mth.ceil(access.woad$getWidth() / scale);
        if (chatX < -4.0 || chatX > width + 8.0) return null;

        int bottom = Mth.floor((mc.getWindow().getGuiScaledHeight() - OFFSET_FROM_BOTTOM) / scale);
        double fromBottom = bottom - chatY;
        if (fromBottom <= 0) return null;

        int row = Mth.ceil(fromBottom / lineHeight) - 1;
        int scrolled = access.woad$scrolledLines();
        int rowsShown = Math.min(visible.size() - scrolled, hud.getLinesPerPage());
        if (row < 0 || row >= rowsShown) return null;

        int index = row + scrolled;
        if (index < 0 || index >= visible.size()) return null;

        GuiMessage parent = visible.get(index).parent();
        if (parent == null) return null;

        String text = parent.content().getString().replaceAll("§.", "").trim();
        return text.isEmpty() ? null : text;
    }
}
