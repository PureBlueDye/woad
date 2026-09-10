package com.pureblue.woad.blackjack.game;

/** The state of an open blackjack table. */
public enum GamePhase {
    /**
     * The table is open but no round is being played: players may join or leave, and {@code !bj start}
     * deals a new round. This is also the state a table returns to after a round finishes, so the same
     * players can simply start again.
     */
    LOBBY,
    /** Cards have been dealt and players are taking turns. */
    IN_ROUND
}
