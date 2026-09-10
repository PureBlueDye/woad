package com.pureblue.woad.blackjack.game;

import java.util.ArrayList;
import java.util.List;

/**
 * A seated player. Normally has a single {@link Hand}; after a split it holds two, played one after
 * the other ({@link #activeIndex()} tracks which one is currently being played).
 */
public final class Player {

    private final String name;
    private final List<Hand> hands = new ArrayList<>();
    private int activeIndex = 0;

    public Player(String name) {
        this.name = name;
        hands.add(new Hand());
    }

    public String name() {
        return name;
    }

    /** All of this player's hands (one, or two after a split). */
    public List<Hand> hands() {
        return hands;
    }

    /** The hand currently being played. */
    public Hand activeHand() {
        return hands.get(activeIndex);
    }

    /** First (or only) hand. Convenience for dealing and single-hand display. */
    public Hand hand() {
        return hands.get(0);
    }

    public int activeIndex() {
        return activeIndex;
    }

    /** True once this player has split into two hands. */
    public boolean isSplit() {
        return hands.size() > 1;
    }

    /** True if there is another (later) hand still to play after the active one. */
    public boolean hasNextHand() {
        return activeIndex < hands.size() - 1;
    }

    public void nextHand() {
        activeIndex++;
    }

    /** Ensures the player has at least {@code count} hands (used when mirroring a split from chat). */
    public void ensureHands(int count) {
        while (hands.size() < count) {
            hands.add(new Hand());
        }
        if (hands.size() > 1) {
            for (Hand h : hands) h.markSplit();
        }
    }

    /** True once every hand is settled (stood, 21, or bust). */
    public boolean isDone() {
        for (Hand h : hands) {
            if (!h.isSettled()) return false;
        }
        return true;
    }

    /** Clears the hands and turn state so this player can play another round. */
    public void reset() {
        hands.clear();
        hands.add(new Hand());
        activeIndex = 0;
    }
}
