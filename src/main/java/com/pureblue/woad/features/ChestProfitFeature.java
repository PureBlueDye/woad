package com.pureblue.woad.features;

import com.pureblue.woad.ai.PriceLookup;
import com.pureblue.woad.chestprofit.ChestReader;
import com.pureblue.woad.chestprofit.MarketPrices;
import com.pureblue.woad.core.Feature;
import com.pureblue.woad.core.setting.BooleanSetting;
import com.pureblue.woad.core.setting.ModeSetting;
import com.pureblue.woad.mixin.HandledScreenAccessor;
import com.pureblue.woad.ui.Draw;
import com.pureblue.woad.ui.Theme;
import com.pureblue.woad.ui.UiText;
import com.pureblue.woad.ui.UiTooltip;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.Slot;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Dungeon chest profit: what each reward chest's loot is worth on the market, minus what opening it
 * costs, shown in a small panel beside the chest screen.
 *
 * <p>In Croesus' run view every chest of the run is listed, most profitable first; resting the
 * pointer on one shows what makes up its value and outlines it in the screen. With a single chest
 * open, the panel lists its loot item by item.
 *
 * <p>Prices come from Coflnet — plain API requests, kept ten minutes — valued either at what
 * selling right away pays or at the cheapest sell offer, as the player chooses.
 */
public class ChestProfitFeature extends Feature {

    private static final String INSTA_SELL = "Insta-sell";
    private static final String SELL_OFFER = "Sell offer";

    private static final int PAD = 6;
    private static final int ROW_H = 11;
    private static final int MIN_W = 120;
    private static final int GAP = 6;

    /** The chest to take, and Croesus' runs with nothing opened yet. */
    private static final int BLUE = 0xFF3B82F6;
    /** The second chest worth a Dungeon Chest Key, and runs where the key can still be used. */
    private static final int DARK_BLUE = 0xFF1E3A8A;

    private final BooleanSetting countEssence = addSetting(new BooleanSetting("Count essence",
            "Add the essence in each chest to its value.", true));

    private final BooleanSetting ignoreFish = addSetting(new BooleanSetting("Ignore fish",
            "Leave the \"... the Fish\" items (Storm the Fish, Maxor the Fish...) out of the value.", true));

    private final ModeSetting priceMode = addSetting(new ModeSetting("Item price",
            "Insta-sell: what selling the loot right now pays. Sell offer: the cheapest sell offer "
                    + "(the instant-buy price, or the lowest BIN on the auction house), which listing "
                    + "it could fetch.",
            INSTA_SELL, List.of(INSTA_SELL, SELL_OFFER)));

    private final UiTooltip tooltip = new UiTooltip();

    /**
     * The slots to colour in the screen being drawn, worked out while it draws its panel and used
     * as it draws its slots — so a change shows one frame later, which nobody can see.
     */
    private final Map<Slot, Integer> highlights = new IdentityHashMap<>();
    private AbstractContainerScreen<?> highlightsOf;

    public ChestProfitFeature() {
        super("chest_profit", "Chest Profit",
                "Shows what each dungeon reward chest is worth, minus its cost, beside the chest "
                        + "screen in Croesus and when a chest is open. Prices from Coflnet.",
                true);
    }

    /** One line of a chest's breakdown: loot in light grey, costs in red, skipped essence muted. */
    private record Row(String label, String amount, int colour, boolean muted) {}

    /** A chest with its loot priced. */
    private record Valued(ChestReader.Chest chest, long profit, boolean pending, List<Row> rows) {}

    // ---- Drawing ------------------------------------------------------------------------------

    /** Called after a container screen has drawn its slots and items. */
    public void renderInContainer(AbstractContainerScreen<?> screen, GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
        highlights.clear();
        highlightsOf = screen;
        if (!(screen.getMenu() instanceof ChestMenu menu)) return;
        String title = plainTitle(screen);
        int containerSlots = menu.getRowCount() * 9;

        if (ChestReader.isCroesus(title)) {
            markRuns(menu, containerSlots);
            return;
        }

        List<Valued> chests = new ArrayList<>();
        boolean single;
        if (ChestReader.isRunView(title)) {
            single = false;
            if (!MarketPrices.namesReady()) {
                drawLoading(screen, ctx);
                return;
            }
            for (ChestReader.Chest chest : ChestReader.readRunView(menu, containerSlots)) chests.add(value(chest));
            if (chests.isEmpty()) return;
            chests.sort(Comparator.comparing(Valued::pending).thenComparing(Valued::profit, Comparator.reverseOrder()));
            markBestChests(menu, chests);
        } else if (ChestReader.isChest(title)) {
            single = true;
            if (!MarketPrices.namesReady()) {
                drawLoading(screen, ctx);
                return;
            }
            ChestReader.Chest chest = ChestReader.readOpenChest(screen.getTitle(), menu, containerSlots);
            if (chest == null) return;
            chests.add(value(chest));
        } else {
            return;
        }

        HandledScreenAccessor gui = (HandledScreenAccessor) screen;
        if (single) {
            drawSingle(screen, gui, ctx, chests.get(0));
        } else {
            drawRun(screen, gui, menu, ctx, chests, mouseX, mouseY);
        }
        tooltip.render(ctx, mouseX, mouseY, screen.width, screen.height);
    }

    /** Every chest of the run: name and profit, the best first. */
    private void drawRun(AbstractContainerScreen<?> screen, HandledScreenAccessor gui, ChestMenu menu,
                         GuiGraphicsExtractor ctx, List<Valued> chests, int mouseX, int mouseY) {
        float w = MIN_W;
        for (Valued v : chests) {
            w = Math.max(w, UiText.width(v.chest().name(), UiText.Style.BODY) + UiText.width(amount(v), UiText.Style.LABEL) + 18 + PAD * 2);
        }
        float h = PAD + 12 + chests.size() * ROW_H + PAD - 2;
        float[] at = place(screen, gui, w);
        float x = at[0];
        float y = at[1];
        frame(ctx, x, y, w, h);

        float rowY = y + PAD + 12;
        for (Valued v : chests) {
            boolean hovered = mouseX >= x && mouseX < x + w && mouseY >= rowY && mouseY < rowY + ROW_H;
            if (hovered) {
                Draw.roundRect(ctx, x + 3, rowY, w - 6, ROW_H, 3f, Theme.HOVER);
                outlineSlot(ctx, gui, menu, v.chest().slot());
                tooltip.offer(v, breakdown(v));
            }
            Integer mark = v.chest().slot() >= 0 ? highlights.get(menu.slots.get(v.chest().slot())) : null;
            if (mark != null) {
                // The same colour as in the screen, so the list and the chests read together.
                Draw.roundRect(ctx, x + 2, rowY + 2, 2, ROW_H - 4, 1f, mark == DARK_BLUE ? 0xFF3354B8 : mark);
            }
            UiText.draw(ctx, v.chest().name(), UiText.Style.BODY, x + PAD,
                    UiText.centerY(UiText.Style.BODY, rowY, ROW_H), v.chest().color());
            drawAmount(ctx, v, x + w - PAD, rowY);
            rowY += ROW_H;
        }
    }

    /** One open chest: its total, then each item and what it adds or costs. */
    private void drawSingle(AbstractContainerScreen<?> screen, HandledScreenAccessor gui, GuiGraphicsExtractor ctx, Valued v) {
        float w = MIN_W;
        for (Row row : v.rows()) {
            w = Math.max(w, UiText.width(row.label(), UiText.Style.BODY) + UiText.width(row.amount(), UiText.Style.BODY) + 14 + PAD * 2);
        }
        float h = PAD + 12 + ROW_H + 4 + v.rows().size() * (ROW_H - 1) + PAD - 2;
        float[] at = place(screen, gui, w);
        float x = at[0];
        float y = at[1];
        frame(ctx, x, y, w, h);

        float rowY = y + PAD + 12;
        UiText.draw(ctx, v.chest().name(), UiText.Style.LABEL, x + PAD,
                UiText.centerY(UiText.Style.LABEL, rowY, ROW_H), v.chest().color());
        drawAmount(ctx, v, x + w - PAD, rowY);
        rowY += ROW_H + 2;
        Draw.rect(ctx, x + PAD, rowY, x + w - PAD, rowY + 1, Theme.LINE);
        rowY += 2;
        for (Row row : v.rows()) {
            float textY = UiText.centerY(UiText.Style.BODY, rowY, ROW_H - 1);
            UiText.draw(ctx, row.label(), UiText.Style.BODY, x + PAD, textY, row.muted() ? Theme.TEXT_3 : Theme.TEXT_2);
            UiText.draw(ctx, row.amount(), UiText.Style.BODY,
                    x + w - PAD - UiText.width(row.amount(), UiText.Style.BODY), textY, row.colour());
            rowY += ROW_H - 1;
        }
    }

    private void drawLoading(AbstractContainerScreen<?> screen, GuiGraphicsExtractor ctx) {
        HandledScreenAccessor gui = (HandledScreenAccessor) screen;
        float[] at = place(screen, gui, MIN_W);
        frame(ctx, at[0], at[1], MIN_W, PAD + 12 + ROW_H + PAD - 2);
        UiText.draw(ctx, "Loading prices…", UiText.Style.BODY, at[0] + PAD,
                UiText.centerY(UiText.Style.BODY, at[1] + PAD + 12, ROW_H), Theme.TEXT_2);
    }

    /** The card and its title line, with the pricing in use on the right. */
    private void frame(GuiGraphicsExtractor ctx, float x, float y, float w, float h) {
        Draw.roundRect(ctx, x, y, w, h, 5f, Theme.TOOLTIP_BG);
        Draw.outline(ctx, x, y, w, h, 5f, 1f, Theme.TOOLTIP_EDGE);
        UiText.draw(ctx, "Chest Profit", UiText.Style.LABEL, x + PAD, y + PAD, Theme.TEXT);
        String mode = useSellOffer() ? SELL_OFFER : INSTA_SELL;
        UiText.draw(ctx, mode, UiText.Style.BODY, x + w - PAD - UiText.width(mode, UiText.Style.BODY),
                y + PAD + 0.5f, Theme.TEXT_3);
    }

    /** Left of the chest screen, or right of it when there is no room on the left. */
    private static float[] place(AbstractContainerScreen<?> screen, HandledScreenAccessor gui, float w) {
        float x = gui.woad$getX() - GAP - w;
        if (x < 4) x = Math.min(gui.woad$getX() + gui.woad$getImageWidth() + GAP, screen.width - w - 4);
        return new float[]{x, gui.woad$getY()};
    }

    private void drawAmount(GuiGraphicsExtractor ctx, Valued v, float right, float rowY) {
        String text = amount(v);
        int colour = v.pending() ? Theme.TEXT_3 : (v.profit() >= 0 ? Theme.OK : Theme.ERR);
        UiText.draw(ctx, text, UiText.Style.LABEL, right - UiText.width(text, UiText.Style.LABEL),
                UiText.centerY(UiText.Style.LABEL, rowY, ROW_H), colour);
    }

    private static void outlineSlot(GuiGraphicsExtractor ctx, HandledScreenAccessor gui, ChestMenu menu, int index) {
        if (index < 0 || index >= menu.slots.size()) return;
        Slot slot = menu.slots.get(index);
        float sx = gui.woad$getX() + slot.x - 1;
        float sy = gui.woad$getY() + slot.y - 1;
        Draw.outline(ctx, sx, sy, 18, 18, 2f, 1.5f, Theme.CYAN);
    }

    // ---- Highlights ---------------------------------------------------------------------------

    /** Called before a slot is drawn, the pose already at the screen's corner. */
    public void highlightSlot(AbstractContainerScreen<?> screen, GuiGraphicsExtractor ctx, Slot slot) {
        if (screen != highlightsOf) return;
        Integer colour = highlights.get(slot);
        if (colour == null) return;
        Draw.roundRect(ctx, slot.x, slot.y, 16, 16, 2f, Draw.withAlpha(colour, colour == DARK_BLUE ? 0.85f : 0.6f));
    }

    /** Croesus' list: runs with nothing opened in blue, runs where the key can still be used in dark blue. */
    private void markRuns(ChestMenu menu, int containerSlots) {
        for (int i = 0; i < containerSlots && i < menu.slots.size(); i++) {
            Slot slot = menu.slots.get(i);
            switch (ChestReader.runState(slot.getItem())) {
                case UNOPENED -> highlights.put(slot, BLUE);
                case KEY_LEFT -> highlights.put(slot, DARK_BLUE);
                default -> { }
            }
        }
    }

    /**
     * A run's chests: the most profitable in blue, and in dark blue the one to open next with a
     * Dungeon Chest Key — only when it still pays once the key is paid for. Once a chest has been
     * claimed, the key chest is the only choice left, so that is the one marked.
     */
    private void markBestChests(ChestMenu menu, List<Valued> chests) {
        List<Valued> open = new ArrayList<>();
        boolean claimed = false;
        for (Valued v : chests) {
            if (v.chest().opened()) claimed = true;
            else if (!v.pending()) open.add(v); // already in profit order
        }
        int next = 0;
        if (!claimed && !open.isEmpty()) {
            mark(menu, open.get(0), BLUE);
            next = 1;
        }
        if (next < open.size() && profitWithKey(open.get(next)) > 0) mark(menu, open.get(next), DARK_BLUE);
    }

    private void mark(ChestMenu menu, Valued v, int colour) {
        int index = v.chest().slot();
        if (index >= 0 && index < menu.slots.size()) highlights.put(menu.slots.get(index), colour);
    }

    /**
     * A chest's profit as a second pick: the key it costs comes off, unless its price already
     * lists the key (and so it was already counted). Not known yet while the key's price loads.
     */
    private long profitWithKey(Valued v) {
        if (v.chest().needsKey()) return v.profit();
        MarketPrices.Price key = MarketPrices.price(ChestReader.KEY_TAG);
        if (key == null) return Long.MIN_VALUE;
        return v.profit() - Math.round(key.value(useSellOffer()));
    }

    // ---- Valuing ------------------------------------------------------------------------------

    private boolean useSellOffer() {
        return SELL_OFFER.equals(priceMode.get());
    }

    /** Prices a chest, keeping a row per item for the breakdown. */
    private Valued value(ChestReader.Chest chest) {
        boolean sellOffer = useSellOffer();
        boolean pending = false;
        long loot = 0;
        List<Row> rows = new ArrayList<>();
        for (ChestReader.Line line : chest.contents()) {
            String label = line.count() > 1 ? line.label() + " x" + line.count() : line.label();
            if ((line.essence() && !countEssence.enabled()) || (isFish(line) && ignoreFish.enabled())) {
                rows.add(new Row(label, "off", Theme.TEXT_3, true));
                continue;
            }
            Long worth = worth(line, sellOffer);
            if (worth == null) {
                pending = true;
                rows.add(new Row(label, "…", Theme.TEXT_3, false));
            } else if (line.tag() == null || worth < 0) {
                rows.add(new Row(label, "?", Theme.WARN, false)); // unknown item: counted as 0
            } else {
                loot += worth;
                rows.add(new Row(label, coins(worth), Theme.TEXT, false));
            }
        }

        long cost = chest.coins();
        if (!chest.costKnown()) {
            rows.add(new Row("Cost", "?", Theme.WARN, false)); // not counted: the profit is the loot alone
        } else if (chest.coins() > 0) {
            rows.add(new Row("Cost", "-" + coins(chest.coins()), Theme.ERR, false));
        }
        for (ChestReader.Line line : chest.costItems()) {
            Long worth = worth(line, sellOffer);
            if (worth == null) {
                pending = true;
            } else if (worth > 0) {
                cost += worth;
                rows.add(new Row(line.label(), "-" + coins(worth), Theme.ERR, false));
            }
        }
        return new Valued(chest, loot - cost, pending, rows);
    }

    /** The "... the Fish" collectibles: Storm the Fish, Maxor the Fish, Chill the Fish (tag CHILL_THE_FISH_2)... */
    private static boolean isFish(ChestReader.Line line) {
        if (line.tag() != null && line.tag().matches(".*_THE_FISH(?:_\\d+)?")) return true;
        return line.label().toLowerCase(java.util.Locale.ROOT).endsWith(" the fish");
    }

    /** What a loot line is worth, {@code null} while its price is on its way, -1 if unknown. */
    private static Long worth(ChestReader.Line line, boolean sellOffer) {
        if (line.tag() == null) return -1L;
        MarketPrices.Price price = MarketPrices.price(line.tag());
        if (price == null) return null;
        if (!price.known()) return -1L;
        return Math.round(price.value(sellOffer) * line.count());
    }

    private List<String> breakdown(Valued v) {
        List<String> lines = new ArrayList<>();
        lines.add(v.chest().name() + ": " + amount(v));
        for (Row row : v.rows()) lines.add(row.label() + ": " + row.amount());
        return lines;
    }

    private static String amount(Valued v) {
        if (v.pending()) return "…";
        return (v.profit() >= 0 ? "+" : "-") + coins(Math.abs(v.profit()));
    }

    private static String coins(long amount) {
        return PriceLookup.coins(amount);
    }

    private static String plainTitle(AbstractContainerScreen<?> screen) {
        String text = ChatFormatting.stripFormatting(screen.getTitle().getString());
        return text == null ? "" : text.trim();
    }
}
