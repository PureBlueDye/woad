package com.pureblue.woad.blackjack.game;

import java.util.ArrayList;
import java.util.List;

/**
 * A single blackjack table: pure game logic with no Minecraft dependencies. Every action returns the
 * lines that should be announced in party chat, so the caller only has to forward them to {@code /pc}.
 *
 * <p>Flow: a table opens in {@link GamePhase#LOBBY} (players join/leave), {@link #begin} deals the
 * cards and moves to {@link GamePhase#IN_ROUND}, players {@link #hit}/{@link #stand}/{@link #split} in
 * join order (a split player finishes their first hand, then the second), then the dealer plays
 * automatically and results are resolved. The table then returns to {@link GamePhase#LOBBY}.
 */
public final class BlackjackGame implements TableView {

    /** The dealer stands once their total reaches this value. */
    private static final int DEALER_STANDS_AT = 17;

    private final List<Player> players = new ArrayList<>();
    private final Hand dealer = new Hand();
    private final String owner;

    private Deck deck;
    private GamePhase phase = GamePhase.LOBBY;
    private int turnIndex = 0;
    /** Whether the dealer's hole card is face-up (true once the dealer plays / the round ends). */
    private boolean dealerRevealed = false;

    /** Opens a new lobby; the player who started it is seated first. */
    public BlackjackGame(String owner) {
        this.owner = owner;
        players.add(new Player(owner));
    }

    public GamePhase phase() {
        return phase;
    }

    /** True when the table is open but nobody is seated, so it can be discarded. */
    public boolean isEmptyLobby() {
        return phase == GamePhase.LOBBY && players.isEmpty();
    }

    // ------------------------------------------------ read-only views (GUI)

    public List<Player> players() {
        return players;
    }

    public Hand dealer() {
        return dealer;
    }

    public boolean isDealerRevealed() {
        return dealerRevealed;
    }

    public String currentTurnName() {
        Player player = current();
        return phase == GamePhase.IN_ROUND && player != null ? player.name() : null;
    }

    public int activeHandIndex() {
        Player player = current();
        return phase == GamePhase.IN_ROUND && player != null ? player.activeIndex() : 0;
    }

    // ----------------------------------------------------------------- lobby

    public List<String> join(String name) {
        List<String> out = new ArrayList<>();
        if (phase != GamePhase.LOBBY) {
            out.add("A round is already in progress. Wait for the next one, " + name + ".");
            return out;
        }
        if (find(name) != null) {
            out.add(name + " is already at the table.");
            return out;
        }
        players.add(new Player(name));
        out.add(name + " joined the table. Players: " + playerNames() + ".");
        return out;
    }

    public List<String> leave(String name) {
        List<String> out = new ArrayList<>();
        Player player = find(name);
        if (player == null) {
            out.add(name + " is not at the table.");
            return out;
        }

        if (phase == GamePhase.LOBBY) {
            players.remove(player);
            out.add(name + " left the table. Players: " + (players.isEmpty() ? "none" : playerNames()) + ".");
            return out;
        }

        if (phase == GamePhase.IN_ROUND) {
            boolean wasTheirTurn = current() == player;
            int idx = players.indexOf(player);
            players.remove(player);
            out.add(name + " left the table and forfeits this round.");
            if (idx < turnIndex) {
                turnIndex--;
            } else if (wasTheirTurn) {
                turnIndex--; // so advanceTurn() lands on the player now occupying this seat
                advanceTurn(out);
            }
        }
        return out;
    }

    // ------------------------------------------------------------- dealing

    /**
     * Deals a new round to the seated players. Works both for the first round and for replays after a
     * previous round finished (the table stays open in {@link GamePhase#LOBBY} between rounds).
     */
    public List<String> begin(String requester) {
        List<String> out = new ArrayList<>();
        if (phase != GamePhase.LOBBY) {
            out.add("A round is already in progress.");
            return out;
        }
        if (players.isEmpty()) {
            out.add("Nobody is at the table. Type !bj join first.");
            return out;
        }

        // Clear any cards left over from a previous round, then shuffle a fresh deck.
        for (Player player : players) {
            player.reset();
        }
        dealer.clear();
        dealerRevealed = false;
        deck = new Deck();
        for (Player player : players) {
            player.hand().add(deck.draw());
            player.hand().add(deck.draw());
        }
        dealer.add(deck.draw());
        dealer.add(deck.draw());
        phase = GamePhase.IN_ROUND;

        out.add("New round! Players: " + playerNames() + ".");
        out.add("Dealer shows " + dealer.cards().get(0).label() + " (second card hidden).");
        for (Player player : players) {
            String line = player.name() + ": " + player.hand().describe() + " (" + player.hand().total() + ")";
            if (player.hand().isBlackjack()) line += " - BLACKJACK!";
            out.add(line);
        }

        turnIndex = 0;
        if (current() != null && current().isDone()) {
            advanceTurn(out);
        } else {
            announceTurn(out);
        }
        return out;
    }

    // -------------------------------------------------------------- actions

    public List<String> hit(String name) {
        List<String> out = new ArrayList<>();
        if (!ensureTurn(name, out)) return out;

        Player player = current();
        Hand hand = player.activeHand();
        hand.add(deck.draw());
        out.add(handLabel(player) + " hits: " + hand.describe() + " (" + hand.total() + ")");

        if (hand.isBust()) {
            out.add(handLabel(player) + " busts" + (player.isSplit() ? "!" : " and loses!"));
            advanceHandOrTurn(out);
        } else if (hand.total() == 21) {
            out.add(handLabel(player) + " has 21!");
            hand.stand();
            advanceHandOrTurn(out);
        }
        return out;
    }

    public List<String> stand(String name) {
        List<String> out = new ArrayList<>();
        if (!ensureTurn(name, out)) return out;

        Player player = current();
        player.activeHand().stand();
        out.add(handLabel(player) + " stands on " + player.activeHand().total() + ".");
        advanceHandOrTurn(out);
        return out;
    }

    /** Splits a starting pair (two cards of equal value) into two hands. */
    public List<String> split(String name) {
        List<String> out = new ArrayList<>();
        if (!ensureTurn(name, out)) return out;

        Player player = current();
        if (!canSplit(player)) {
            out.add("You can only split two cards of the same value on your first move, " + name + ".");
            return out;
        }

        Hand first = player.activeHand();
        Card moved = first.cards().remove(1);
        Hand second = new Hand();
        second.add(moved);
        first.markSplit();
        second.markSplit();
        first.add(deck.draw());
        second.add(deck.draw());
        player.hands().add(second);

        out.add(name + " splits the pair!");
        out.add(name + " hand 1: " + first.describe() + " (" + first.total() + ")");
        out.add(name + " hand 2: " + second.describe() + " (" + second.total() + ")");

        // Split aces get a single card each and then stand automatically (standard rule).
        if (moved.rank().isAce()) {
            first.stand();
            second.stand();
            out.add("Split aces each get one card.");
            advanceHandOrTurn(out);
        } else {
            announceTurn(out);
        }
        return out;
    }

    // -------------------------------------------------------------- helpers

    private boolean ensureTurn(String name, List<String> out) {
        if (phase != GamePhase.IN_ROUND) {
            out.add("No round is in progress. Type !bj start to open a table.");
            return false;
        }
        Player player = find(name);
        if (player == null) {
            out.add(name + " is not in this round.");
            return false;
        }
        if (current() != player) {
            Player turn = current();
            out.add("It's not your turn, " + name + "." + (turn != null ? " Waiting on " + turn.name() + "." : ""));
            return false;
        }
        return true;
    }

    /** A player may split only on an untouched starting pair of equal-value cards. */
    private boolean canSplit(Player player) {
        if (player.isSplit()) return false;
        Hand hand = player.activeHand();
        return hand.cards().size() == 2
                && hand.cards().get(0).rank().value() == hand.cards().get(1).rank().value();
    }

    /** After a hand is settled, move to the player's next hand, or to the next player. */
    private void advanceHandOrTurn(List<String> out) {
        Player player = current();
        if (player.hasNextHand()) {
            player.nextHand();
            Hand hand = player.activeHand();
            out.add(player.name() + " plays hand " + (player.activeIndex() + 1) + ": "
                    + hand.describe() + " (" + hand.total() + ")");
            if (hand.isSettled()) {
                advanceHandOrTurn(out);
            } else {
                announceTurn(out);
            }
        } else {
            advanceTurn(out);
        }
    }

    private void advanceTurn(List<String> out) {
        for (int i = turnIndex + 1; i < players.size(); i++) {
            if (!players.get(i).isDone()) {
                turnIndex = i;
                announceTurn(out);
                return;
            }
        }
        playDealerAndResolve(out);
    }

    private void announceTurn(List<String> out) {
        Player player = current();
        if (player == null) return;
        String options = "!hit, !stand" + (canSplit(player) ? ", !split" : "");
        out.add("Your turn, " + handLabel(player) + "! (" + player.activeHand().total()
                + ") Type " + options + ".");
    }

    private void playDealerAndResolve(List<String> out) {
        boolean anyoneStanding = players.stream()
                .flatMap(p -> p.hands().stream())
                .anyMatch(h -> !h.isBust());

        dealerRevealed = true;
        out.add("Dealer reveals " + dealer.describe() + " (" + dealer.total() + ").");
        if (anyoneStanding) {
            while (dealer.total() < DEALER_STANDS_AT) {
                Card drawn = deck.draw();
                dealer.add(drawn);
                out.add("Dealer draws " + drawn.label() + " -> " + dealer.describe() + " (" + dealer.total() + ").");
            }
            if (dealer.isBust()) {
                out.add("Dealer busts on " + dealer.total() + "!");
            } else {
                out.add("Dealer stands on " + dealer.total() + ".");
            }
        } else {
            out.add("Everyone busted - the dealer wins by default.");
        }

        resolve(out);
        phase = GamePhase.LOBBY;
        out.add("Round over! Type !bj start to play again, or !bj close to close the table.");
    }

    private void resolve(List<String> out) {
        int dealerTotal = dealer.total();
        boolean dealerBust = dealer.isBust();
        boolean dealerBlackjack = dealer.isBlackjack();

        for (Player player : players) {
            for (int h = 0; h < player.hands().size(); h++) {
                Hand hand = player.hands().get(h);
                int total = hand.total();
                String result;
                if (hand.isBust()) {
                    result = "LOSE (busted with " + total + ")";
                } else if (hand.isBlackjack() && !dealerBlackjack) {
                    result = "WIN with a blackjack!";
                } else if (dealerBust) {
                    result = "WIN (" + total + " vs dealer bust)";
                } else if (total > dealerTotal) {
                    result = "WIN (" + total + " vs " + dealerTotal + ")";
                } else if (total < dealerTotal) {
                    result = "LOSE (" + total + " vs " + dealerTotal + ")";
                } else {
                    result = "PUSH (" + total + " vs " + dealerTotal + ")";
                }
                String label = player.isSplit() ? player.name() + " hand " + (h + 1) : player.name();
                out.add(label + ": " + result);
            }
        }
    }

    /** "Name" normally, or "Name hand 2" once the player has split (identifies which hand acted). */
    private String handLabel(Player player) {
        return player.isSplit() ? player.name() + " hand " + (player.activeIndex() + 1) : player.name();
    }

    private Player current() {
        if (turnIndex < 0 || turnIndex >= players.size()) return null;
        return players.get(turnIndex);
    }

    private Player find(String name) {
        for (Player player : players) {
            if (player.name().equalsIgnoreCase(name)) return player;
        }
        return null;
    }

    private String playerNames() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < players.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(players.get(i).name());
        }
        return sb.toString();
    }
}
