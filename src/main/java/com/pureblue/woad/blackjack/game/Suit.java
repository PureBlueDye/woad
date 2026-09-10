package com.pureblue.woad.blackjack.game;

/** The four card suits, with a short symbol used when printing a card to chat. */
public enum Suit {
    SPADES("♠"),
    HEARTS("♥"),
    DIAMONDS("♦"),
    CLUBS("♣");

    private final String symbol;

    Suit(String symbol) {
        this.symbol = symbol;
    }

    public String symbol() {
        return symbol;
    }

    /** Resolves a suit from its chat symbol (e.g. {@code "♥"}), or {@code null} if unknown. */
    public static Suit fromSymbol(String symbol) {
        for (Suit suit : values()) {
            if (suit.symbol.equals(symbol)) return suit;
        }
        return null;
    }
}
