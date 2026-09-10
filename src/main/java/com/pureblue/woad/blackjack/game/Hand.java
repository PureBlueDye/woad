package com.pureblue.woad.blackjack.game;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/** A collection of cards with blackjack scoring (Aces auto-counted as 11 or 1). */
public final class Hand {

    private final List<Card> cards = new ArrayList<>();
    private boolean stood = false;
    /** False once this hand comes from a split: a 21 here is not a natural blackjack. */
    private boolean natural = true;

    public void add(Card card) {
        if (card != null) cards.add(card);
    }

    /** Empties the hand so it can be reused for a new round. */
    public void clear() {
        cards.clear();
        stood = false;
        natural = true;
    }

    public List<Card> cards() {
        return cards;
    }

    /**
     * Best total that does not exceed 21. Each Ace starts at 11 and is demoted to 1 (subtracting 10)
     * one at a time while the total is over 21.
     */
    public int total() {
        int total = 0;
        int aces = 0;
        for (Card card : cards) {
            total += card.rank().value();
            if (card.rank().isAce()) aces++;
        }
        while (total > 21 && aces > 0) {
            total -= 10;
            aces--;
        }
        return total;
    }

    public boolean isBust() {
        return total() > 21;
    }

    /** A natural blackjack: exactly two cards totalling 21, not the product of a split. */
    public boolean isBlackjack() {
        return natural && cards.size() == 2 && total() == 21;
    }

    public boolean hasStood() {
        return stood;
    }

    public void stand() {
        this.stood = true;
    }

    /** Marks this hand as coming from a split (so a later 21 is not treated as a natural blackjack). */
    public void markSplit() {
        this.natural = false;
    }

    /** True once the hand can take no further action: the player stood, or the total is 21+ (or bust). */
    public boolean isSettled() {
        return stood || total() >= 21;
    }

    /** Space-separated card labels, e.g. {@code "A♠ 10♥"}. */
    public String describe() {
        return cards.stream().map(Card::label).collect(Collectors.joining(" "));
    }
}
