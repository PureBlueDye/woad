package com.pureblue.woad.blackjack.game;

/** An immutable playing card. */
public record Card(Rank rank, Suit suit) {

    /** Short label for chat, e.g. {@code "A♠"} or {@code "10♥"}. */
    public String label() {
        return rank.label() + suit.symbol();
    }
}
