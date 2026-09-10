package com.pureblue.woad.blackjack;

import com.pureblue.woad.blackjack.chat.ChatChannel;
import com.pureblue.woad.blackjack.chat.ChatOutput;
import com.pureblue.woad.blackjack.chat.PartyChatParser;
import com.pureblue.woad.blackjack.chat.PartyChatParser.PartyMessage;
import com.pureblue.woad.blackjack.game.BlackjackGame;
import com.pureblue.woad.blackjack.game.GamePhase;
import com.pureblue.woad.blackjack.game.TableMirror;
import com.pureblue.woad.blackjack.game.TableView;
import net.minecraft.client.Minecraft;

/**
 * Turns party/guild-chat commands into game actions and forwards the resulting status lines back to
 * the same channel the table is running in.
 *
 * <p><b>Authority:</b> only the client whose player created the table runs the actual
 * {@link BlackjackGame} ({@link #game} is non-null) and broadcasts status. Every other client keeps a
 * {@link TableMirror} rebuilt from those broadcasts so its GUI shows the same table. This prevents
 * multiple modded clients from each dealing their own cards or spamming duplicate replies.
 *
 * <p>All methods run on the client thread (the chat interceptor marshals onto it), so the static
 * state needs no locking.
 */
public final class BlackjackManager {

    /** The authoritative table, set only on the host (the creator's client). */
    private static BlackjackGame game;
    /** The channel the host's table is bound to. */
    private static ChatChannel channel;
    /** A reconstruction of the table from chat, used by non-host clients for display. */
    private static final TableMirror mirror = new TableMirror();

    private BlackjackManager() {}

    /** The table to display: the authoritative game on the host, else the chat-reconstructed mirror. */
    public static TableView currentView() {
        if (game != null) return game;
        return mirror.isActive() ? mirror : null;
    }

    /** The channel of the active table (host's binding, or the mirrored table's channel). */
    public static ChatChannel currentChannel() {
        if (channel != null) return channel;
        return mirror.isActive() ? mirror.channel() : null;
    }

    /** Entry point for every incoming chat line (already color-stripped). */
    public static void onChatLine(String line) {
        PartyMessage message = PartyChatParser.parse(line);
        if (message == null) return;

        String body = message.body().trim();

        // Every party/guild line feeds the mirror; it only reacts to the host's status formats.
        mirror.accept(message.channel(), body);

        if (body.isEmpty() || body.charAt(0) != '!') return;

        String[] tokens = body.split("\\s+");
        String first = tokens[0].toLowerCase();
        ChatChannel from = message.channel();
        String sender = message.sender();

        // When this very client just sent a command, hold our reply for an interval so it does not
        // collide with that command and get dropped by Hypixel's command throttle.
        if (isLocal(sender)) ChatOutput.deferForLocalCommand();

        switch (first) {
            case "!hit" -> handleAction(from, sender, Action.HIT);
            case "!stand" -> handleAction(from, sender, Action.STAND);
            case "!split" -> handleAction(from, sender, Action.SPLIT);
            case "!bj" -> {
                String sub = tokens.length >= 2 ? tokens[1].toLowerCase() : "";
                switch (sub) {
                    case "create" -> handleCreate(from, sender);
                    case "start" -> handleStart(from, sender);
                    case "close" -> handleClose(from, sender);
                    case "join" -> handleJoin(from, sender);
                    case "leave" -> handleLeave(from, sender);
                    case "hit" -> handleAction(from, sender, Action.HIT);     // alias for !hit
                    case "stand" -> handleAction(from, sender, Action.STAND); // alias for !stand
                    case "split" -> handleAction(from, sender, Action.SPLIT); // alias for !split
                    case "help" -> { if (isLocal(sender)) sendHelp(from); }
                    default -> { if (isLocal(sender)) ChatOutput.send(from, "Unknown command. Type !bj help for the list."); }
                }
            }
            default -> { /* not a blackjack command */ }
        }
    }

    private enum Action { HIT, STAND, SPLIT }

    private static void handleCreate(ChatChannel from, String sender) {
        if (game != null) {
            // I am the host already.
            if (from == channel) {
                ChatOutput.send(channel, "A table is already open. Type !bj start to deal, or !bj close to close it.");
            }
            return;
        }
        if (mirror.isActive()) {
            return; // another client already hosts a table; the host will reply if needed
        }
        // No table anywhere: only the creator's own client becomes the host.
        if (!isLocal(sender)) return;
        game = new BlackjackGame(sender);
        channel = from;
        ChatOutput.send(from, sender + " opened a blackjack table! Type !bj join to sit, then !bj start to deal.");
    }

    private static void handleStart(ChatChannel from, String sender) {
        if (game == null) {
            if (isLocal(sender) && !mirror.isActive()) {
                ChatOutput.send(from, "There is no open table. Type !bj create to open one.");
            }
            return;
        }
        if (from != channel) return;
        ChatOutput.sendAll(channel, game.begin(sender));
    }

    private static void handleClose(ChatChannel from, String sender) {
        if (game == null) {
            if (isLocal(sender) && !mirror.isActive()) {
                ChatOutput.send(from, "There is no table to close.");
            }
            return;
        }
        if (from != channel) return;
        game = null;
        channel = null;
        ChatOutput.send(from, sender + " closed the blackjack table.");
    }

    private static void handleJoin(ChatChannel from, String sender) {
        if (game == null) {
            if (isLocal(sender) && !mirror.isActive()) {
                ChatOutput.send(from, "There is no open table. Type !bj create to open one.");
            }
            return;
        }
        if (from != channel) return;
        ChatOutput.sendAll(channel, game.join(sender));
    }

    private static void handleAction(ChatChannel from, String sender, Action action) {
        if (game == null) {
            if (isLocal(sender) && !mirror.isActive()) {
                ChatOutput.send(from, "No round is in progress. Type !bj start to open a table.");
            }
            return;
        }
        if (from != channel) return;
        ChatOutput.sendAll(channel, switch (action) {
            case HIT -> game.hit(sender);
            case STAND -> game.stand(sender);
            case SPLIT -> game.split(sender);
        });
    }

    private static void handleLeave(ChatChannel from, String sender) {
        if (game == null) {
            if (isLocal(sender) && !mirror.isActive()) {
                ChatOutput.send(from, "There is no table to leave.");
            }
            return;
        }
        if (from != channel) return;
        ChatOutput.sendAll(channel, game.leave(sender));
        // Discard a table once everyone has left it.
        if (game.isEmptyLobby()) {
            game = null;
            channel = null;
        }
    }

    private static void sendHelp(ChatChannel from) {
        ChatOutput.send(from, "Blackjack: !bj create (open table), !bj join, !bj start (deal), "
                + "!hit, !stand, !split, !bj leave, !bj close, !bj help");
    }

    /** True when {@code sender} is the local player (the only one allowed to host their own table). */
    /** Test seam: when set, used instead of the live session username (no Minecraft in tests). */
    static String testLocalName = null;

    private static boolean isLocal(String sender) {
        if (testLocalName != null) return testLocalName.equalsIgnoreCase(sender);
        return Minecraft.getInstance().getUser().getName().equalsIgnoreCase(sender);
    }

    /** Clears all state when leaving a server. */
    public static void reset() {
        game = null;
        channel = null;
        mirror.reset();
        ChatOutput.clear();
    }
}
