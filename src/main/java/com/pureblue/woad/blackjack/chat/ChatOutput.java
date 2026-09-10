package com.pureblue.woad.blackjack.chat;

import java.util.ArrayDeque;
import java.util.Deque;
import net.minecraft.client.Minecraft;

/**
 * Sends status lines back to the Hypixel chat the game is running in, via {@code /pc} (party) or
 * {@code /gc} (guild) depending on each message's {@link ChatChannel}.
 *
 * <p>One line is sent per action, spaced {@link #TICKS_BETWEEN_MESSAGES} apart, to stay under
 * Hypixel's "sending commands too fast" guard. Crucially, when the local player issues a command the
 * reply is also held for one interval ({@link #deferForLocalCommand()}): otherwise the bot's reply
 * leaves the same client a split second after the player's own command and Hypixel drops it.
 */
public final class ChatOutput {

    /** Ticks to wait between two outgoing messages (~1s at 20 TPS). */
    private static final int TICKS_BETWEEN_MESSAGES = 20;

    private record Outgoing(ChatChannel channel, String message) {}

    private static final Deque<Outgoing> QUEUE = new ArrayDeque<>();
    private static int cooldown = 0;

    private ChatOutput() {}

    /** Queues a single line to be sent to the given channel. */
    public static void send(ChatChannel channel, String message) {
        if (message != null && !message.isBlank()) {
            QUEUE.add(new Outgoing(channel, message));
        }
    }

    /** Queues several lines at once for the given channel, preserving order. */
    public static void sendAll(ChatChannel channel, Iterable<String> messages) {
        for (String message : messages) {
            send(channel, message);
        }
    }

    /**
     * Holds the next outgoing message for one interval. Call this when the local player just issued a
     * command, so the bot's reply does not leave this client immediately after the player's own
     * command (which Hypixel would treat as commands sent too fast and drop the reply).
     */
    public static void deferForLocalCommand() {
        cooldown = Math.max(cooldown, TICKS_BETWEEN_MESSAGES);
    }

    /** Called every client tick to release at most one queued message once the cooldown elapses. */
    public static void tick() {
        if (cooldown > 0) {
            cooldown--;
            return;
        }
        if (QUEUE.isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.getConnection() == null) {
            // Not connected yet; hold the message until we are.
            return;
        }
        Outgoing out = QUEUE.poll();
        mc.getConnection().sendCommand(out.channel().command() + " " + out.message());
        cooldown = TICKS_BETWEEN_MESSAGES;
    }

    /** Drops any pending messages (used when the player disconnects). */
    public static void clear() {
        QUEUE.clear();
        cooldown = 0;
    }
}
