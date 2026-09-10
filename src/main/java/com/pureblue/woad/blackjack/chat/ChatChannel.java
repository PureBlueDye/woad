package com.pureblue.woad.blackjack.chat;

/** A Hypixel chat channel the game can run in: party ({@code /pc}) or guild ({@code /gc}). */
public enum ChatChannel {
    PARTY("Party", "pc"),
    GUILD("Guild", "gc");

    private final String label;
    private final String command;

    ChatChannel(String label, String command) {
        this.label = label;
        this.command = command;
    }

    /** The prefix Hypixel prints in chat, e.g. {@code "Party"} in {@code "Party > Name: ..."}. */
    public String label() {
        return label;
    }

    /** The command (without slash) used to talk in this channel, e.g. {@code "pc"} or {@code "gc"}. */
    public String command() {
        return command;
    }

    /** Resolves the channel from the {@code "Party"}/{@code "Guild"} label, or {@code null}. */
    public static ChatChannel fromLabel(String label) {
        for (ChatChannel channel : values()) {
            if (channel.label.equalsIgnoreCase(label)) return channel;
        }
        return null;
    }
}
