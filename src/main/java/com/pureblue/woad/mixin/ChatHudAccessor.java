package com.pureblue.woad.mixin;

import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.List;

/**
 * Opens up the private state needed to tell which chat line the mouse is over.
 *
 * <p>There is no public API for it: hover detection happens inside the chat's own renderer while
 * drawing, so the hit test has to be redone from the geometry used by {@code ChatComponent.render}.
 */
@Mixin(ChatComponent.class)
public interface ChatHudAccessor {

    /** Wrapped display lines, newest first; several per message when a message wraps. */
    @Accessor("trimmedMessages")
    List<GuiMessage.Line> woad$visibleMessages();

    /** Whole messages, newest first — this is where the untruncated text lives. */
    @Accessor("allMessages")
    List<GuiMessage> woad$messages();

    @Accessor("chatScrollbarPos")
    int woad$scrolledLines();

    @Invoker("getLineHeight")
    int woad$getLineHeight();

    @Invoker("getScale")
    double woad$getChatScale();

    @Invoker("getWidth")
    int woad$getWidth();
}
