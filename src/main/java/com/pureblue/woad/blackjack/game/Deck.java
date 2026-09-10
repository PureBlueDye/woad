package com.pureblue.woad.blackjack.game;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** A standard 52-card deck, shuffled on creation and drawn from the top. */
public final class Deck {

    private final List<Card> cards = new ArrayList<>(52);

    public Deck() {
        for (Suit suit : Suit.values()) {
            for (Rank rank : Rank.values()) {
                cards.add(new Card(rank, suit));
            }
        }
        Collections.shuffle(cards);
    }

    /** Draws the top card. Returns {@code null} only if the deck is somehow empty. */
    public Card draw() {
        if (cards.isEmpty()) return null;
        return cards.remove(cards.size() - 1);
    }
}
