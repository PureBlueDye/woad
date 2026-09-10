package com.pureblue.woad.blackjack.game;

import java.util.List;

/**
 * A read-only view of a blackjack table for the GUI. Implemented both by the authoritative
 * {@link BlackjackGame} (on the host that runs the table) and by {@link TableMirror} (reconstructed
 * from chat on every other client), so the screen renders the same way for everyone.
 */
public interface TableView {

    /** Whether a round is being played or the table is between rounds. */
    GamePhase phase();

    /** The seated players, in turn order. */
    List<Player> players();

    /** The dealer's hand (during play the hole card is a placeholder; see {@link #isDealerRevealed()}). */
    Hand dealer();

    /** True once the dealer's hole card is face-up. */
    boolean isDealerRevealed();

    /** The name of the player whose turn it is, or {@code null} when no one is acting. */
    String currentTurnName();

    /** Which hand of the current player is active (0 normally, 1 for the second hand of a split). */
    int activeHandIndex();
}
