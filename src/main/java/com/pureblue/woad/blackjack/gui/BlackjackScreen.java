package com.pureblue.woad.blackjack.gui;

import com.pureblue.woad.blackjack.BlackjackManager;
import com.pureblue.woad.blackjack.chat.ChatChannel;
import com.pureblue.woad.blackjack.game.Card;
import com.pureblue.woad.blackjack.game.GamePhase;
import com.pureblue.woad.blackjack.game.Hand;
import com.pureblue.woad.blackjack.game.Player;
import com.pureblue.woad.blackjack.game.Suit;
import com.pureblue.woad.blackjack.game.TableView;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.joml.Matrix3x2fStack;

/**
 * A blackjack table drawn like a real casino one: a green felt half-circle with the dealer along the
 * straight (top) edge and the players seated around the curved (bottom) edge, each with their cards
 * fanned in front of them. The player whose turn it is has their name drawn larger and in gold. A bar
 * of buttons sends the mod's commands so the whole game can be driven from here.
 */
public class BlackjackScreen extends Screen {

    // ---- Palette --------------------------------------------------------------------------
    private static final int OVERLAY   = 0x1A000000; // ~10% black, a light dim over the world
    private static final int BORDER    = 0xFF34343C;
    private static final int PANEL     = 0xFF1B1B20;
    private static final int HEADER    = 0xFF202028;
    private static final int FELT      = 0xFF0E5C32;
    private static final int FELT_EDGE = 0xFF0A4326;
    private static final int FELT_LINE = 0x66F4E9C8;
    private static final int TEXT      = 0xFFE8E8EC;
    private static final int TEXT_DIM  = 0xFF9A9AA4;
    private static final int GOLD      = 0xFFFFC83D;
    private static final int RED_SUIT  = 0xFFC0202E;
    private static final int BLK_SUIT  = 0xFF161620;
    private static final int CARD_BG   = 0xFFF4F1E9;
    private static final int CARD_EDGE = 0xFF24241F;
    private static final int BACK_BG   = 0xFF24366E;
    private static final int BACK_IN   = 0xFF3A52A6;
    private static final int BTN       = 0xFF2A2A33;
    private static final int BTN_HOVER = 0xFF353541;
    private static final int BTN_ON    = 0xFF2E6F46;
    private static final int BTN_DIS   = 0xFF202024;
    private static final int BTN_TEXT  = 0xFFE8E8EC;
    private static final int BTN_TDIS  = 0xFF5A5A62;

    // ---- Layout ---------------------------------------------------------------------------
    private static final int PANEL_W = 470;
    private static final int PANEL_H = 300;
    private static final int HEADER_H = 28;
    private static final int PAD = 12;
    private static final int CW = 22;          // card width
    private static final int CH = 32;          // card height
    private static final int FAN = 14;         // horizontal offset between fanned cards
    private static final int BTN_H = 20;

    private ChatChannel selectedChannel = ChatChannel.PARTY;

    private final List<Button> buttons = new ArrayList<>();

    private record Button(int x1, int y1, int x2, int y2, String label, boolean enabled,
                          boolean primary, Runnable onClick) {}

    public BlackjackScreen() {
        super(Component.literal("Blackjack"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        // No-op: the vanilla background applies the menu blur + darkening, which we don't want.
    }

    // ---- Render ---------------------------------------------------------------------------

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, this.width, this.height, OVERLAY);

        int x = (this.width - PANEL_W) / 2;
        int y = (this.height - PANEL_H) / 2;

        panel(context, x, y, x + PANEL_W, y + PANEL_H, PANEL, BORDER);
        context.fill(x + 1, y + 1, x + PANEL_W - 1, y + HEADER_H, HEADER);
        context.text(this.font, "Blackjack", x + PAD, y + 10, GOLD, false);

        TableView view = BlackjackManager.currentView();
        ChatChannel active = BlackjackManager.currentChannel();
        drawStatus(context, x, y, view, active);

        int feltTop = y + HEADER_H + 6;
        int row1Y = y + PANEL_H - 50;
        int feltBottom = row1Y - 8;
        int cx = x + PANEL_W / 2;
        int rx = (PANEL_W - 2 * PAD) / 2;
        int ry = feltBottom - feltTop;

        drawFelt(context, cx, feltTop, rx, ry);

        // Center markings.
        drawCentered(context, "BLACKJACK PAYS 3 TO 2", cx, feltTop + (int) (ry * 0.42), FELT_LINE);
        drawCentered(context, "Dealer must stand on 17", cx, feltTop + (int) (ry * 0.42) + 11, FELT_LINE);

        if (view == null) {
            drawCentered(context, "No table open - pick a channel and click Create.", cx, feltTop + 30, TEXT_DIM);
        } else {
            drawDealer(context, cx, feltTop + 6, view);
            drawSeats(context, view, cx, feltTop, rx, ry);
        }

        buttons.clear();
        layoutButtons(context, x, y, mouseX, mouseY, view);
    }

    private void drawStatus(GuiGraphicsExtractor context, int x, int y, TableView view, ChatChannel active) {
        String status;
        if (view == null) {
            status = "Channel: " + selectedChannel.label();
        } else {
            String phase = view.phase() == GamePhase.IN_ROUND ? "In round" : "Lobby";
            status = (active != null ? active.label() : "?") + " - " + phase;
        }
        context.text(this.font, status,
                x + PANEL_W - PAD - this.font.width(status), y + 10, TEXT_DIM, false);
    }

    /** A green felt half-ellipse: straight edge on top (dealer), curved edge below (players). */
    private void drawFelt(GuiGraphicsExtractor context, int cx, int top, int rx, int ry) {
        for (int dy = 0; dy <= ry; dy++) {
            double f = (double) dy / ry;
            int hw = (int) (rx * Math.sqrt(Math.max(0, 1 - f * f)));
            int edgeHw = hw;
            int innerHw = Math.max(0, hw - 3);
            int yy = top + dy;
            context.fill(cx - edgeHw, yy, cx - innerHw, yy + 1, FELT_EDGE);
            context.fill(cx + innerHw, yy, cx + edgeHw, yy + 1, FELT_EDGE);
            context.fill(cx - innerHw, yy, cx + innerHw, yy + 1, FELT);
        }
        // Straight top edge band.
        context.fill(cx - rx, top, cx + rx, top + 2, FELT_EDGE);
        // Decorative inner arc line.
        int ir = ry - 24;
        int irx = rx - 24;
        if (ir > 0 && irx > 0) {
            for (int dy = 0; dy <= ir; dy++) {
                double f = (double) dy / ir;
                int hw = (int) (irx * Math.sqrt(Math.max(0, 1 - f * f)));
                int yy = top + 12 + dy;
                context.fill(cx - hw, yy, cx - hw + 1, yy + 1, FELT_LINE);
                context.fill(cx + hw - 1, yy, cx + hw, yy + 1, FELT_LINE);
            }
        }
    }

    private void drawDealer(GuiGraphicsExtractor context, int cx, int topY, TableView view) {
        Hand dealer = view.dealer();
        List<Card> cards = dealer.cards();
        if (cards.isEmpty()) {
            drawCentered(context, "DEALER", cx, topY + 14, TEXT);
            return;
        }
        drawCardsCentered(context, cx, topY, cards, true, view.isDealerRevealed());
        String total = view.isDealerRevealed()
                ? "DEALER  " + dealer.total()
                : "DEALER  " + cards.get(0).rank().value() + "+?";
        drawCentered(context, total, cx, topY + CH + 2, TEXT);
    }

    /** Players seated around the curved bottom edge, each lower toward the middle like a real table. */
    private void drawSeats(GuiGraphicsExtractor context, TableView view, int cx, int feltTop, int rx, int ry) {
        List<Player> players = view.players();
        if (players.isEmpty()) {
            drawCentered(context, "No players seated - click Join.", cx, feltTop + ry - 30, TEXT_DIM);
            return;
        }
        String turn = view.currentTurnName();
        int activeHand = view.activeHandIndex();
        int n = players.size();
        double spread = rx * 0.78;

        for (int i = 0; i < n; i++) {
            Player player = players.get(i);
            double fx = n == 1 ? 0.5 : (i + 0.5) / n;
            int seatX = (int) (cx + (fx - 0.5) * 2 * spread);
            double frac = (double) (seatX - cx) / rx;
            int edgeY = feltTop + (int) (ry * Math.sqrt(Math.max(0, 1 - frac * frac)));
            boolean isTurn = player.name().equalsIgnoreCase(turn);
            drawSeat(context, player, seatX, edgeY - 6, isTurn, activeHand);
        }
    }

    private void drawSeat(GuiGraphicsExtractor context, Player player, int seatX, int bottomY,
                          boolean isTurn, int activeHand) {
        List<Hand> hands = player.hands();
        int nameY = bottomY - 9;
        int cardsTopY = nameY - CH - 3;

        if (hands.size() == 1) {
            drawCardsCentered(context, seatX, cardsTopY, hands.get(0).cards(), false, true);
            drawHandTag(context, hands.get(0), seatX, cardsTopY - 10, false);
        } else {
            // Split: two compact hands side by side, active one tagged in gold.
            int half = 34;
            for (int h = 0; h < hands.size(); h++) {
                int hx = seatX + (h == 0 ? -half : half);
                drawCardsCentered(context, hx, cardsTopY, hands.get(h).cards(), false, true);
                boolean activeHere = isTurn && h == activeHand;
                drawHandTag(context, hands.get(h), hx, cardsTopY - 10, activeHere);
                drawCentered(context, "#" + (h + 1), hx, cardsTopY + CH + 1, activeHere ? GOLD : TEXT_DIM);
            }
        }

        if (isTurn) {
            drawScaledCentered(context, player.name(), seatX, nameY + 3, 1.25f, GOLD);
        } else {
            drawCentered(context, player.name(), seatX, nameY, TEXT);
        }
    }

    /** Small total/status tag above a hand (BUST / BLACKJACK / total). */
    private void drawHandTag(GuiGraphicsExtractor context, Hand hand, int cx, int y, boolean active) {
        String tag;
        int color;
        if (hand.cards().isEmpty()) {
            return;
        } else if (hand.isBust()) {
            tag = "BUST " + hand.total();
            color = RED_SUIT;
        } else if (hand.isBlackjack()) {
            tag = "BJ";
            color = GOLD;
        } else if (hand.hasStood()) {
            tag = "STAND " + hand.total();
            color = TEXT_DIM;
        } else {
            tag = String.valueOf(hand.total());
            color = active ? GOLD : TEXT;
        }
        drawCentered(context, tag, cx, y, color);
    }

    // ---- Buttons --------------------------------------------------------------------------

    private void layoutButtons(GuiGraphicsExtractor context, int x, int y, int mouseX, int mouseY, TableView view) {
        boolean noTable = view == null;
        boolean lobby = view != null && view.phase() == GamePhase.LOBBY;
        boolean inRound = view != null && view.phase() == GamePhase.IN_ROUND;
        boolean hasPlayers = view != null && !view.players().isEmpty();

        // Channel toggle lives in the header (only meaningful before a table exists).
        String chanLabel = noTable ? "Chat: " + selectedChannel.label()
                : (BlackjackManager.currentChannel() != null ? BlackjackManager.currentChannel().label() : "Chat");
        int ctw = 78;
        addButton(context, x + PANEL_W / 2 - ctw / 2, y + 5, ctw, 16, chanLabel, noTable, false,
                this::toggleChannel, mouseX, mouseY);

        int gap = 6;
        int left = x + PAD;
        int right = x + PANEL_W - PAD;
        int colW = (right - left - gap * 3) / 4;
        int row1 = y + PANEL_H - 50;
        int row2 = row1 + BTN_H + gap;

        addButton(context, col(left, colW, gap, 0), row1, colW, BTN_H, "Create", noTable, false,
                () -> send("!bj create"), mouseX, mouseY);
        addButton(context, col(left, colW, gap, 1), row1, colW, BTN_H, "Join", lobby, false,
                () -> send("!bj join"), mouseX, mouseY);
        addButton(context, col(left, colW, gap, 2), row1, colW, BTN_H, "Start", lobby && hasPlayers, true,
                () -> send("!bj start"), mouseX, mouseY);
        addButton(context, col(left, colW, gap, 3), row1, colW, BTN_H, "Close", !noTable, false,
                () -> send("!bj close"), mouseX, mouseY);

        addButton(context, col(left, colW, gap, 0), row2, colW, BTN_H, "Hit", inRound, true,
                () -> send("!hit"), mouseX, mouseY);
        addButton(context, col(left, colW, gap, 1), row2, colW, BTN_H, "Stand", inRound, true,
                () -> send("!stand"), mouseX, mouseY);
        addButton(context, col(left, colW, gap, 2), row2, colW, BTN_H, "Split", inRound, false,
                () -> send("!split"), mouseX, mouseY);
        addButton(context, col(left, colW, gap, 3), row2, colW, BTN_H, "Leave", !noTable, false,
                () -> send("!bj leave"), mouseX, mouseY);
    }

    private static int col(int left, int colW, int gap, int i) {
        return left + i * (colW + gap);
    }

    private void addButton(GuiGraphicsExtractor context, int bx, int by, int w, int h, String label, boolean enabled,
                           boolean primary, Runnable onClick, int mouseX, int mouseY) {
        int x2 = bx + w;
        int y2 = by + h;
        boolean hovered = enabled && inside(mouseX, mouseY, bx, by, x2, y2);
        int bg = !enabled ? BTN_DIS : (primary ? BTN_ON : (hovered ? BTN_HOVER : BTN));
        fillRound(context, bx, by, x2, y2, bg);
        int color = enabled ? BTN_TEXT : BTN_TDIS;
        context.text(this.font, label,
                bx + (w - this.font.width(label)) / 2, by + (h - 8) / 2, color, false);
        buttons.add(new Button(bx, by, x2, y2, label, enabled, primary, onClick));
    }

    private void toggleChannel() {
        selectedChannel = selectedChannel == ChatChannel.PARTY ? ChatChannel.GUILD : ChatChannel.PARTY;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        if (click.button() == 0) {
            int mx = (int) click.x();
            int my = (int) click.y();
            for (Button b : buttons) {
                if (b.enabled() && inside(mx, my, b.x1(), b.y1(), b.x2(), b.y2())) {
                    b.onClick().run();
                    return true;
                }
            }
        }
        return super.mouseClicked(click, doubled);
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

    private void drawCardsCentered(GuiGraphicsExtractor context, int centerX, int topY, List<Card> cards,
                                   boolean dealerHidden, boolean revealed) {
        if (cards.isEmpty()) return;
        int span = (cards.size() - 1) * FAN + CW;
        int startX = centerX - span / 2;
        for (int i = 0; i < cards.size(); i++) {
            int cxp = startX + i * FAN;
            if (dealerHidden && i == 1 && !revealed) {
                drawCardBack(context, cxp, topY);
            } else {
                drawCard(context, cxp, topY, cards.get(i));
            }
        }
    }

    private void drawCard(GuiGraphicsExtractor context, int x, int y, Card card) {
        fillRound(context, x + 1, y + 1, x + CW + 1, y + CH + 1, 0x55000000); // shadow
        panel(context, x, y, x + CW, y + CH, CARD_BG, CARD_EDGE);
        int suitColor = (card.suit() == Suit.HEARTS || card.suit() == Suit.DIAMONDS) ? RED_SUIT : BLK_SUIT;
        context.text(this.font, card.rank().label(), x + 2, y + 2, suitColor, false);
        drawScaledCentered(context, card.suit().symbol(), x + CW / 2, y + CH / 2 + 3, 1.5f, suitColor);
    }

    private void drawCardBack(GuiGraphicsExtractor context, int x, int y) {
        fillRound(context, x + 1, y + 1, x + CW + 1, y + CH + 1, 0x55000000);
        panel(context, x, y, x + CW, y + CH, BACK_BG, CARD_EDGE);
        fillRound(context, x + 3, y + 3, x + CW - 3, y + CH - 3, BACK_IN);
        drawScaledCentered(context, "?", x + CW / 2, y + CH / 2 + 3, 1.4f, 0xFFD8E0FF);
    }

    // ---- Drawing helpers ------------------------------------------------------------------

    private void drawCentered(GuiGraphicsExtractor context, String text, int cx, int y, int color) {
        context.text(this.font, text, cx - this.font.width(text) / 2, y, color, false);
    }

    private void drawScaledCentered(GuiGraphicsExtractor context, String text, int cx, int cy, float scale, int color) {
        Matrix3x2fStack m = context.pose();
        m.pushMatrix();
        m.translate(cx, cy);
        m.scale(scale);
        context.text(this.font, text, -this.font.width(text) / 2, -4, color, false);
        m.popMatrix();
    }

    private static void panel(GuiGraphicsExtractor context, int x1, int y1, int x2, int y2, int fill, int border) {
        fillRound(context, x1, y1, x2, y2, border);
        fillRound(context, x1 + 1, y1 + 1, x2 - 1, y2 - 1, fill);
    }

    private static void fillRound(GuiGraphicsExtractor context, int x1, int y1, int x2, int y2, int color) {
        if (x2 - x1 < 2 || y2 - y1 < 2) {
            context.fill(x1, y1, x2, y2, color);
            return;
        }
        context.fill(x1 + 1, y1, x2 - 1, y2, color);
        context.fill(x1, y1 + 1, x2, y2 - 1, color);
    }

    private static boolean inside(int px, int py, int x1, int y1, int x2, int y2) {
        return px >= x1 && px <= x2 && py >= y1 && py <= y2;
    }
}
