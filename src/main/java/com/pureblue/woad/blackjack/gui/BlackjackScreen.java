package com.pureblue.woad.blackjack.gui;

import com.pureblue.woad.blackjack.BlackjackManager;
import com.pureblue.woad.blackjack.chat.ChatChannel;
import com.pureblue.woad.blackjack.game.Card;
import com.pureblue.woad.blackjack.game.GamePhase;
import com.pureblue.woad.blackjack.game.Hand;
import com.pureblue.woad.blackjack.game.Player;
import com.pureblue.woad.blackjack.game.Suit;
import com.pureblue.woad.blackjack.game.TableView;
import com.pureblue.woad.ui.Draw;
import com.pureblue.woad.ui.Theme;
import com.pureblue.woad.ui.UiButton;
import com.pureblue.woad.ui.UiPanel;
import com.pureblue.woad.ui.UiScreen;
import com.pureblue.woad.ui.UiSegmented;
import com.pureblue.woad.ui.UiText;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import org.joml.Matrix3x2fStack;

import java.util.List;

/**
 * A blackjack table drawn like a real casino one: a green felt half-circle with the dealer along the
 * straight (top) edge and the players seated around the curved (bottom) edge, each with their cards
 * fanned in front of them. The player whose turn it is has their name drawn larger and in gold. A bar
 * of buttons sends the mod's commands so the whole game can be driven from here.
 *
 * <p>The felt keeps its casino green; everything around it follows Woad's interface.
 */
public class BlackjackScreen extends UiScreen {

    // ---- Table colours ------------------------------------------------------------------------
    private static final int FELT_CENTRE = 0xFF157A48;
    private static final int FELT_RIM    = 0xFF0B4D2B;
    private static final int FELT_EDGE   = 0xFF073A20;
    private static final int FELT_LINE   = 0x66F4E9C8;
    private static final int GOLD        = 0xFFFFC83D;
    private static final int RED_SUIT    = 0xFFC0202E;
    private static final int BLK_SUIT    = 0xFF161620;
    private static final int CARD_BG     = 0xFFF6F3EC;
    private static final int CARD_EDGE   = 0x40000000;

    // ---- Layout ---------------------------------------------------------------------------
    private static final int PANEL_W = 470;
    private static final int PANEL_H = 300;
    private static final int HEADER_H = 30;
    private static final int PAD = 12;
    private static final int CW = 22;          // card width
    private static final int CH = 32;          // card height
    private static final int FAN = 14;         // horizontal offset between fanned cards
    private static final int BTN_H = 18;

    private ChatChannel selectedChannel = ChatChannel.PARTY;

    private final UiSegmented channel = add(new UiSegmented(List.of("Party", "Guild"),
            () -> selectedChannel == ChatChannel.PARTY ? 0 : 1,
            index -> selectedChannel = index == 0 ? ChatChannel.PARTY : ChatChannel.GUILD));
    private final UiButton create = add(new UiButton("Create", UiButton.Variant.SECONDARY, () -> send("!bj create")));
    private final UiButton join = add(new UiButton("Join", UiButton.Variant.SECONDARY, () -> send("!bj join")));
    private final UiButton start = add(new UiButton("Start", UiButton.Variant.PRIMARY, () -> send("!bj start")));
    private final UiButton closeTable = add(new UiButton("Close", UiButton.Variant.SECONDARY, () -> send("!bj close")));
    private final UiButton hit = add(new UiButton("Hit", UiButton.Variant.PRIMARY, () -> send("!hit")));
    private final UiButton stand = add(new UiButton("Stand", UiButton.Variant.PRIMARY, () -> send("!stand")));
    private final UiButton split = add(new UiButton("Split", UiButton.Variant.SECONDARY, () -> send("!split")));
    private final UiButton leave = add(new UiButton("Leave", UiButton.Variant.SECONDARY, () -> send("!bj leave")));

    public BlackjackScreen() {
        super(Component.literal("Blackjack"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        // A light dim only: the table is meant to sit over the game, not hide it behind a blur.
        Draw.rect(context, 0, 0, this.width, this.height, 0x260B1220);
    }

    // ---- Render ---------------------------------------------------------------------------

    @Override
    protected void renderContent(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        float x = Math.round((this.width - PANEL_W) / 2f);
        float y = Math.round((this.height - PANEL_H) / 2f);
        UiPanel.panel(context, x, y, PANEL_W, PANEL_H);

        TableView view = BlackjackManager.currentView();
        ChatChannel active = BlackjackManager.currentChannel();
        renderHeader(context, x, y, view, active, mouseX, mouseY);

        float feltTop = y + HEADER_H + 7;
        float row1Y = y + PANEL_H - PAD - BTN_H * 2 - 6;
        float feltBottom = row1Y - 9;
        float cx = x + PANEL_W / 2f;
        float rx = (PANEL_W - 2 * PAD) / 2f;
        float ry = feltBottom - feltTop;

        drawFelt(context, cx, feltTop, rx, ry);

        // Centre markings.
        float markY = feltTop + ry * 0.42f;
        UiText.drawCentered(context, "BLACKJACK PAYS 3 TO 2", UiText.Style.LABEL, cx, markY, FELT_LINE);
        UiText.drawCentered(context, "Dealer must stand on 17", UiText.Style.BODY, cx, markY + 11, FELT_LINE);

        if (view == null) {
            UiText.drawCentered(context, "No table open - pick a channel and click Create.", UiText.Style.BODY,
                    cx, feltTop + 30, 0xCCFFFFFF);
        } else {
            drawDealer(context, cx, feltTop + 6, view);
            drawSeats(context, view, cx, feltTop, rx, ry);
        }

        layoutButtons(context, x, y, mouseX, mouseY, view);
    }

    private void renderHeader(GuiGraphicsExtractor context, float x, float y, TableView view, ChatChannel active,
                              int mouseX, int mouseY) {
        UiText.draw(context, "Blackjack", UiText.Style.TITLE, x + 14, UiText.centerY(UiText.Style.TITLE, y, HEADER_H), Theme.TEXT);

        // The chat channel can only be chosen before a table exists; after that it is fixed.
        boolean noTable = view == null;
        channel.visible(noTable).enabled(noTable);
        if (noTable) {
            float cw = channel.preferredWidth();
            channel.bounds(x + PANEL_W / 2f - cw / 2f, y + (HEADER_H - 16) / 2f, cw, 16).render(context, mouseX, mouseY);
        } else {
            String label = active != null ? active.label() : "Chat";
            float pw = UiText.width(label, UiText.Style.LABEL) + 12;
            UiPanel.pill(context, label, x + PANEL_W / 2f - pw / 2f, y + (HEADER_H - 12) / 2f, Theme.CYAN, Theme.GLOW_SOFT);
        }

        String status;
        int colour;
        int fill;
        if (noTable) {
            status = "No table";
            colour = Theme.TEXT_2;
            fill = Theme.NEUTRAL_TINT;
        } else if (view.phase() == GamePhase.IN_ROUND) {
            status = "In round";
            colour = Theme.OK;
            fill = Theme.OK_TINT;
        } else {
            status = "Lobby";
            colour = Theme.CYAN;
            fill = Theme.GLOW_SOFT;
        }
        float sw = UiText.width(status, UiText.Style.LABEL) + 12;
        UiPanel.pill(context, status, x + PANEL_W - 14 - sw, y + (HEADER_H - 12) / 2f, colour, fill);
        UiPanel.hairline(context, x, x + PANEL_W, y + HEADER_H);
    }

    /** A green felt half-ellipse: straight edge on top (dealer), curved edge below (players). */
    private void drawFelt(GuiGraphicsExtractor context, float cx, float top, float rx, float ry) {
        Draw.halfEllipse(context, cx, top, rx, ry, FELT_CENTRE, FELT_RIM);
        // A padded rim along the curve and the straight dealer's edge.
        Draw.halfEllipseStroke(context, cx, top, rx - 1.5f, ry - 1.5f, 3f, FELT_EDGE);
        Draw.rect(context, cx - rx, top, cx + rx, top + 2.5f, FELT_EDGE);
        // Decorative inner arc line.
        float irx = rx - 24;
        float iry = ry - 24;
        if (irx > 0 && iry > 0) {
            Draw.halfEllipseStroke(context, cx, top + 12, irx, iry, 1f, FELT_LINE);
        }
    }

    private void drawDealer(GuiGraphicsExtractor context, float cx, float topY, TableView view) {
        Hand dealer = view.dealer();
        List<Card> cards = dealer.cards();
        if (cards.isEmpty()) {
            UiText.drawCentered(context, "DEALER", UiText.Style.LABEL, cx, topY + 14, Theme.TEXT);
            return;
        }
        drawCardsCentered(context, cx, topY, cards, true, view.isDealerRevealed());
        String total = view.isDealerRevealed()
                ? "DEALER  " + dealer.total()
                : "DEALER  " + cards.get(0).rank().value() + "+?";
        UiText.drawCentered(context, total, UiText.Style.LABEL, cx, topY + CH + 4, Theme.TEXT);
    }

    /** Players seated around the curved bottom edge, each lower toward the middle like a real table. */
    private void drawSeats(GuiGraphicsExtractor context, TableView view, float cx, float feltTop, float rx, float ry) {
        List<Player> players = view.players();
        if (players.isEmpty()) {
            UiText.drawCentered(context, "No players seated - click Join.", UiText.Style.BODY, cx, feltTop + ry - 30, 0xCCFFFFFF);
            return;
        }
        String turn = view.currentTurnName();
        int activeHand = view.activeHandIndex();
        int n = players.size();
        double spread = rx * 0.78;

        for (int i = 0; i < n; i++) {
            Player player = players.get(i);
            double fx = n == 1 ? 0.5 : (i + 0.5) / n;
            float seatX = (float) (cx + (fx - 0.5) * 2 * spread);
            double frac = (seatX - cx) / rx;
            float edgeY = feltTop + (float) (ry * Math.sqrt(Math.max(0, 1 - frac * frac)));
            boolean isTurn = player.name().equalsIgnoreCase(turn);
            drawSeat(context, player, seatX, edgeY - 6, isTurn, activeHand);
        }
    }

    private void drawSeat(GuiGraphicsExtractor context, Player player, float seatX, float bottomY,
                          boolean isTurn, int activeHand) {
        List<Hand> hands = player.hands();
        float nameY = bottomY - 9;
        float cardsTopY = nameY - CH - 4;

        if (hands.size() == 1) {
            drawCardsCentered(context, seatX, cardsTopY, hands.get(0).cards(), false, true);
            drawHandTag(context, hands.get(0), seatX, cardsTopY - 10, false);
        } else {
            // Split: two compact hands side by side, active one tagged in gold.
            int half = 34;
            for (int h = 0; h < hands.size(); h++) {
                float hx = seatX + (h == 0 ? -half : half);
                drawCardsCentered(context, hx, cardsTopY, hands.get(h).cards(), false, true);
                boolean activeHere = isTurn && h == activeHand;
                drawHandTag(context, hands.get(h), hx, cardsTopY - 10, activeHere);
                UiText.drawCentered(context, "#" + (h + 1), UiText.Style.LABEL, hx, cardsTopY + CH + 2,
                        activeHere ? GOLD : Theme.TEXT_2);
            }
        }

        if (isTurn) {
            // The player to act: gold name on a soft gold glow.
            float w = UiText.width(player.name(), UiText.Style.LABEL) + 12;
            Draw.shadow(context, seatX - w / 2f, nameY - 3, w, 12, 6f, 5f, 0f, 0x55FFC83D);
            Draw.roundRect(context, seatX - w / 2f, nameY - 3, w, 12, 6f, 0x33FFC83D);
            UiText.drawCentered(context, player.name(), UiText.Style.LABEL, seatX, nameY, GOLD);
        } else {
            UiText.drawCentered(context, player.name(), UiText.Style.LABEL, seatX, nameY, Theme.TEXT);
        }
    }

    /** Small total/status tag above a hand (BUST / BLACKJACK / total). */
    private void drawHandTag(GuiGraphicsExtractor context, Hand hand, float cx, float y, boolean active) {
        String tag;
        int color;
        if (hand.cards().isEmpty()) {
            return;
        } else if (hand.isBust()) {
            tag = "BUST " + hand.total();
            color = 0xFFFF6B6B;
        } else if (hand.isBlackjack()) {
            tag = "BJ";
            color = GOLD;
        } else if (hand.hasStood()) {
            tag = "STAND " + hand.total();
            color = 0xFFB8C4D6;
        } else {
            tag = String.valueOf(hand.total());
            color = active ? GOLD : Theme.TEXT;
        }
        UiText.drawCentered(context, tag, UiText.Style.LABEL, cx, y, color);
    }

    // ---- Buttons --------------------------------------------------------------------------

    private void layoutButtons(GuiGraphicsExtractor context, float x, float y, int mouseX, int mouseY, TableView view) {
        boolean noTable = view == null;
        boolean lobby = view != null && view.phase() == GamePhase.LOBBY;
        boolean inRound = view != null && view.phase() == GamePhase.IN_ROUND;
        boolean hasPlayers = view != null && !view.players().isEmpty();

        create.enabled(noTable);
        join.enabled(lobby);
        start.enabled(lobby && hasPlayers);
        closeTable.enabled(!noTable);
        hit.enabled(inRound);
        stand.enabled(inRound);
        split.enabled(inRound);
        leave.enabled(!noTable);

        float gap = 6;
        float left = x + PAD;
        float right = x + PANEL_W - PAD;
        float colW = (right - left - gap * 3) / 4;
        float row1 = y + PANEL_H - PAD - BTN_H * 2 - 6;
        float row2 = row1 + BTN_H + 6;

        UiButton[] top = {create, join, start, closeTable};
        UiButton[] bottom = {hit, stand, split, leave};
        for (int i = 0; i < 4; i++) {
            top[i].bounds(left + i * (colW + gap), row1, colW, BTN_H).render(context, mouseX, mouseY);
            bottom[i].bounds(left + i * (colW + gap), row2, colW, BTN_H).render(context, mouseX, mouseY);
        }
    }

    // ---- Command sending ------------------------------------------------------------------

    private ChatChannel sendChannel() {
        ChatChannel active = BlackjackManager.currentChannel();
        return active != null ? active : selectedChannel;
    }

    private void send(String bjCommand) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() == null) return;
        mc.getConnection().sendCommand(sendChannel().command() + " " + bjCommand);
    }

    // ---- Card drawing ---------------------------------------------------------------------

    private void drawCardsCentered(GuiGraphicsExtractor context, float centerX, float topY, List<Card> cards,
                                   boolean dealerHidden, boolean revealed) {
        if (cards.isEmpty()) return;
        float span = (cards.size() - 1) * FAN + CW;
        float startX = centerX - span / 2f;
        for (int i = 0; i < cards.size(); i++) {
            float cxp = startX + i * FAN;
            if (dealerHidden && i == 1 && !revealed) {
                drawCardBack(context, cxp, topY);
            } else {
                drawCard(context, cxp, topY, cards.get(i));
            }
        }
    }

    private void drawCard(GuiGraphicsExtractor context, float x, float y, Card card) {
        Draw.shadow(context, x, y, CW, CH, 2.5f, 4f, 1.5f, 0x66000000);
        Draw.roundRectV(context, x, y, CW, CH, 2.5f, CARD_BG, 0xFFE9E4D8);
        Draw.outline(context, x, y, CW, CH, 2.5f, 1f, CARD_EDGE);
        int suitColor = (card.suit() == Suit.HEARTS || card.suit() == Suit.DIAMONDS) ? RED_SUIT : BLK_SUIT;
        UiText.draw(context, card.rank().label(), UiText.Style.LABEL, x + 2.5f, y + 3, suitColor);
        drawScaledCentered(context, card.suit().symbol(), x + CW / 2f, y + CH / 2f + 3, 1.6f, suitColor);
    }

    private void drawCardBack(GuiGraphicsExtractor context, float x, float y) {
        Draw.shadow(context, x, y, CW, CH, 2.5f, 4f, 1.5f, 0x66000000);
        Draw.roundRectDiagonal(context, x, y, CW, CH, 2.5f, Theme.FILL, Theme.FILL_DEEP);
        Draw.outline(context, x + 2.5f, y + 2.5f, CW - 5, CH - 5, 1.5f, 1f, 0x80BFD7FF);
        drawScaledCentered(context, "?", x + CW / 2f, y + CH / 2f + 1, 1.4f, 0xFFD8E4FF);
    }

    // ---- Drawing helpers ------------------------------------------------------------------

    /** Draws {@code text} centred on (cx, cy) at a scale, e.g. a card's suit. */
    private void drawScaledCentered(GuiGraphicsExtractor context, String text, float cx, float cy, float scale, int color) {
        Matrix3x2fStack m = context.pose();
        m.pushMatrix();
        m.translate(cx, cy);
        m.scale(scale);
        float previous = Draw.pushScale(scale);
        UiText.Style style = UiText.Style.LABEL;
        UiText.draw(context, text, style, -UiText.width(text, style) / 2f, -UiText.capHeight(style) / 2f - 2f, color);
        Draw.popScale(previous);
        m.popMatrix();
    }
}
