package com.pureblue.woad.blackjack.game;

/**
 * A card rank and its blackjack value. Face cards count as 10; the Ace counts as 11 here and is
 * demoted to 1 when needed by {@link Hand#total()}.
 */
public enum Rank {
    TWO("2", 2),
    THREE("3", 3),
    FOUR("4", 4),
    FIVE("5", 5),
    SIX("6", 6),
    SEVEN("7", 7),
    EIGHT("8", 8),
    NINE("9", 9),
    TEN("10", 10),
    JACK("J", 10),
    QUEEN("Q", 10),
    KING("K", 10),
    ACE("A", 11);

    private final String label;
    private final int value;

    Rank(String label, int value) {
        this.label = label;
        this.value = value;
    }

    public String label() {
        return label;
    }

    public int value() {
        return value;
    }

    public boolean isAce() {
        return this == ACE;
    }

    /** Resolves a rank from its chat label (e.g. {@code "10"}, {@code "K"}), or {@code null}. */
    public static Rank fromLabel(String label) {
        for (Rank rank : values()) {
            if (rank.label.equals(label)) return rank;
        }
        return null;
    }
}
