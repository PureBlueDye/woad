package com.pureblue.woad.blackjack.game;

import com.pureblue.woad.blackjack.chat.ChatChannel;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A best-effort reconstruction of the table for clients that are not running the game. The host
 * broadcasts every state change to party/guild chat in human-readable lines; this class parses those
 * lines back into a {@link TableView} so non-host players see the same table in their GUI.
 *
 * <p>It deliberately only reacts to lines that match the host's exact status formats, so ordinary chat
 * is ignored. Totals/bust/blackjack are recomputed from the parsed cards via {@link Hand}.
 */
public final class TableMirror implements TableView {

    private static final String CARDS = "[0-9JQKA♠♥♦♣ ]+";

    private static final Pattern OPENED      = Pattern.compile("^([A-Za-z0-9_]{1,16}) opened a blackjack table!.*$");
    private static final Pattern NEW_ROUND   = Pattern.compile("^New round! Players: (.+?)\\.?$");
    private static final Pattern JOINED      = Pattern.compile("^[A-Za-z0-9_]{1,16} joined the table\\. Players: (.+?)\\.?$");
    private static final Pattern LEFT_LOBBY  = Pattern.compile("^[A-Za-z0-9_]{1,16} left the table\\. Players: (.+?)\\.?$");
    private static final Pattern FORFEIT     = Pattern.compile("^([A-Za-z0-9_]{1,16}) left the table and forfeits this round\\.$");
    private static final Pattern DEALER_SHOW = Pattern.compile("^Dealer shows (\\S+) \\(second card hidden\\)\\.$");
    private static final Pattern DEALER_FULL = Pattern.compile("^Dealer (?:reveals|draws \\S+ ->) (" + CARDS + ") \\((\\d+)\\)\\.$");
    private static final Pattern SPLIT       = Pattern.compile("^([A-Za-z0-9_]{1,16}) splits the pair!$");
    private static final Pattern PLAYS_HAND  = Pattern.compile("^([A-Za-z0-9_]{1,16}) plays hand (\\d+): (" + CARDS + ") \\((\\d+)\\)$");
    private static final Pattern HIT_HAND    = Pattern.compile("^([A-Za-z0-9_]{1,16}) hand (\\d+) hits: (" + CARDS + ") \\((\\d+)\\)$");
    private static final Pattern STAND_HAND  = Pattern.compile("^([A-Za-z0-9_]{1,16}) hand (\\d+) stands on \\d+\\.$");
    private static final Pattern DEAL_HAND   = Pattern.compile("^([A-Za-z0-9_]{1,16}) hand (\\d+): (" + CARDS + ") \\((\\d+)\\)$");
    private static final Pattern TURN        = Pattern.compile("^Your turn, ([A-Za-z0-9_]{1,16})(?: hand (\\d+))?!.*$");
    private static final Pattern HIT         = Pattern.compile("^([A-Za-z0-9_]{1,16}) hits: (" + CARDS + ") \\((\\d+)\\)$");
    private static final Pattern STAND       = Pattern.compile("^([A-Za-z0-9_]{1,16}) stands on \\d+\\.$");
    private static final Pattern DEAL        = Pattern.compile("^([A-Za-z0-9_]{1,16}): (" + CARDS + ") \\((\\d+)\\)(?: - BLACKJACK!)?$");
    private static final Pattern ROUND_OVER  = Pattern.compile("^Round over!.*$");

    /** A placeholder for the dealer's hidden card so the GUI draws a card back during play. */
    private static final Card HIDDEN = new Card(Rank.TWO, Suit.SPADES);

    private final Map<String, Player> seats = new LinkedHashMap<>();
    private final Hand dealer = new Hand();
    private GamePhase phase = GamePhase.LOBBY;
    private boolean active = false;
    private boolean dealerRevealed = false;
    private String currentTurn;
    private int currentHandIndex = 0;
    private ChatChannel channel;

    public boolean isActive() {
        return active;
    }

    public ChatChannel channel() {
        return channel;
    }

    public void reset() {
        seats.clear();
        dealer.clear();
        phase = GamePhase.LOBBY;
        active = false;
        dealerRevealed = false;
        currentTurn = null;
        currentHandIndex = 0;
        channel = null;
    }

    /** Feeds one chat line (a status line broadcast by the host) into the mirror. */
    public void accept(ChatChannel from, String body) {
        Matcher m;

        if ((m = OPENED.matcher(body)).matches()) {
            reset();
            active = true;
            channel = from;
            phase = GamePhase.LOBBY;
            seat(m.group(1));
        } else if (body.contains("closed the blackjack table.")) {
            reset();
        } else if ((m = NEW_ROUND.matcher(body)).matches()) {
            active = true;
            channel = from;
            phase = GamePhase.IN_ROUND;
            dealerRevealed = false;
            currentTurn = null;
            currentHandIndex = 0;
            dealer.clear();
            syncPlayers(m.group(1));
            for (Player p : seats.values()) p.reset();
        } else if ((m = JOINED.matcher(body)).matches()) {
            active = true;
            channel = from;
            phase = GamePhase.LOBBY;
            syncPlayers(m.group(1));
        } else if ((m = LEFT_LOBBY.matcher(body)).matches()) {
            channel = from;
            syncPlayers(m.group(1));
            if (seats.isEmpty()) reset();
        } else if ((m = FORFEIT.matcher(body)).matches()) {
            seats.remove(m.group(1).toLowerCase());
        } else if ((m = DEALER_SHOW.matcher(body)).matches()) {
            active = true;
            channel = from;
            phase = GamePhase.IN_ROUND;
            dealer.clear();
            dealer.add(parseCard(m.group(1)));
            dealer.add(HIDDEN);
            dealerRevealed = false;
        } else if ((m = DEALER_FULL.matcher(body)).matches()) {
            setHand(dealer, m.group(1));
            dealerRevealed = true;
        } else if (body.startsWith("Dealer ") || body.startsWith("Everyone busted")) {
            // Dealer stands/busts lines: no card data, and must not look like a player line.
        } else if ((m = SPLIT.matcher(body)).matches()) {
            seat(m.group(1)).ensureHands(2);
        } else if ((m = PLAYS_HAND.matcher(body)).matches()) {
            int idx = Integer.parseInt(m.group(2)) - 1;
            setPlayerHand(m.group(1), idx, m.group(3));
            currentTurn = m.group(1);
            currentHandIndex = idx;
        } else if ((m = HIT_HAND.matcher(body)).matches()) {
            setPlayerHand(m.group(1), Integer.parseInt(m.group(2)) - 1, m.group(3));
        } else if ((m = STAND_HAND.matcher(body)).matches()) {
            Player p = seat(m.group(1));
            int idx = Integer.parseInt(m.group(2)) - 1;
            p.ensureHands(idx + 1);
            p.hands().get(idx).stand();
        } else if ((m = DEAL_HAND.matcher(body)).matches()) {
            setPlayerHand(m.group(1), Integer.parseInt(m.group(2)) - 1, m.group(3));
        } else if ((m = TURN.matcher(body)).matches()) {
            active = true;
            channel = from;
            phase = GamePhase.IN_ROUND;
            currentTurn = m.group(1);
            currentHandIndex = m.group(2) != null ? Integer.parseInt(m.group(2)) - 1 : 0;
        } else if ((m = HIT.matcher(body)).matches()) {
            setPlayerHand(m.group(1), 0, m.group(2));
        } else if ((m = STAND.matcher(body)).matches()) {
            seat(m.group(1)).hand().stand();
        } else if (ROUND_OVER.matcher(body).matches()) {
            phase = GamePhase.LOBBY;
            currentTurn = null;
        } else if ((m = DEAL.matcher(body)).matches()) {
            setPlayerHand(m.group(1), 0, m.group(2));
        }
    }

    // ---- TableView ------------------------------------------------------------------------

    @Override
    public GamePhase phase() {
        return phase;
    }

    @Override
    public List<Player> players() {
        return new ArrayList<>(seats.values());
    }

    @Override
    public Hand dealer() {
        return dealer;
    }

    @Override
    public boolean isDealerRevealed() {
        return dealerRevealed;
    }

    @Override
    public String currentTurnName() {
        return phase == GamePhase.IN_ROUND ? currentTurn : null;
    }

    @Override
    public int activeHandIndex() {
        return phase == GamePhase.IN_ROUND ? currentHandIndex : 0;
    }

    // ---- Helpers --------------------------------------------------------------------------

    private void syncPlayers(String csv) {
        Map<String, Player> old = new LinkedHashMap<>(seats);
        seats.clear();
        for (String raw : csv.split(",")) {
            String name = raw.trim();
            if (name.isEmpty() || name.equalsIgnoreCase("none")) continue;
            Player existing = old.get(name.toLowerCase());
            seats.put(name.toLowerCase(), existing != null ? existing : new Player(name));
        }
    }

    private Player seat(String name) {
        return seats.computeIfAbsent(name.toLowerCase(), k -> new Player(name));
    }

    private void setPlayerHand(String name, int handIndex, String cards) {
        Player p = seat(name);
        p.ensureHands(handIndex + 1);
        Hand hand = p.hands().get(handIndex);
        setHand(hand, cards);
        // setHand() clears the split flag; restore it so a split 21 is not shown as a blackjack.
        if (p.hands().size() > 1) hand.markSplit();
    }

    private void setHand(Hand hand, String cards) {
        hand.clear();
        for (Card card : parseCards(cards)) hand.add(card);
    }

    private static List<Card> parseCards(String text) {
        List<Card> cards = new ArrayList<>();
        for (String token : text.trim().split("\\s+")) {
            Card card = parseCard(token);
            if (card != null) cards.add(card);
        }
        return cards;
    }

    private static Card parseCard(String token) {
        if (token == null || token.length() < 2) return null;
        Suit suit = Suit.fromSymbol(token.substring(token.length() - 1));
        Rank rank = Rank.fromLabel(token.substring(0, token.length() - 1));
        return (suit != null && rank != null) ? new Card(rank, suit) : null;
    }
}
