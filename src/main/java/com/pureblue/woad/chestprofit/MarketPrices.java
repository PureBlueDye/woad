package com.pureblue.woad.chestprofit;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.pureblue.woad.ai.CoflnetClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Item prices from Coflnet, kept for ten minutes, and the table that turns an item's display name
 * into its SkyBlock tag.
 *
 * <p>Nothing here blocks the game: a lookup returns what is known right now and, when that is
 * missing or older than ten minutes, asks Coflnet in the background. The chest overlay simply shows
 * "…" until the answer lands, and keeps showing the previous price while a refresh is running.
 *
 * <p>One endpoint covers every item: {@code /item/price/{tag}/current} answers for bazaar products
 * (instant sell and instant buy) and for auction items (Coflnet's quick-sell estimate and the lowest
 * BIN) alike, so a chest of mixed loot never needs to know which market each item trades on.
 */
public final class MarketPrices {

    private static final Logger LOGGER = LoggerFactory.getLogger("Woad");

    /** How long a price is trusted before it is asked again. */
    private static final long PRICE_TTL_MS = 10 * 60_000L;
    /** A failed request is retried sooner, but not on every frame. */
    private static final long RETRY_MS = 60_000L;
    /** Item names barely change; the table is reloaded a few times a day at most. */
    private static final long NAMES_TTL_MS = 6 * 3_600_000L;

    /**
     * What one unit of an item is worth.
     *
     * @param instaSell what selling it right now pays: the best bazaar buy order, or Coflnet's
     *                  quick-sell estimate for an auction item
     * @param sellOffer what listing it asks: the cheapest bazaar sell offer (the instant-buy price),
     *                  or the lowest BIN for an auction item
     * @param known     false when Coflnet has never heard of the tag
     */
    public record Price(double instaSell, double sellOffer, boolean known) {

        public double value(boolean useSellOffer) {
            if (useSellOffer) return sellOffer > 0 ? sellOffer : Math.max(0, instaSell);
            // Nobody buys it at all right now (an empty bazaar order book): it sells for nothing.
            return Math.max(0, instaSell);
        }
    }

    private record Cached(Price price, long expiresAt) {
        boolean stale(long now) {
            return now >= expiresAt;
        }
    }

    private static final Map<String, Cached> PRICES = new ConcurrentHashMap<>();
    private static final Set<String> IN_FLIGHT = ConcurrentHashMap.newKeySet();

    /** Few threads on purpose: Coflnet's free API is rate limited, and a chest needs a few dozen prices. */
    private static final ExecutorService POOL = Executors.newFixedThreadPool(4, task -> {
        Thread thread = new Thread(task, "Woad prices");
        thread.setDaemon(true);
        return thread;
    });

    private static volatile Map<String, String> tagsByName = Map.of();
    private static volatile Set<String> knownTags = Set.of();
    private static volatile long namesLoadedAt;
    private static final AtomicBoolean namesLoading = new AtomicBoolean();

    private MarketPrices() {}

    // ---- Prices ---------------------------------------------------------------------------------

    /**
     * The price of one unit, or {@code null} while it has not arrived yet. Asks Coflnet in the
     * background when the price is missing or out of date.
     */
    public static Price price(String tag) {
        long now = System.currentTimeMillis();
        Cached cached = PRICES.get(tag);
        if ((cached == null || cached.stale(now)) && IN_FLIGHT.add(tag)) {
            POOL.execute(() -> fetch(tag));
        }
        return cached == null ? null : cached.price();
    }

    private static void fetch(String tag) {
        try {
            Price price = null;
            JsonElement body = CoflnetClient.api("/item/price/" + tag + "/current");
            if (body != null && body.isJsonObject()) {
                JsonObject json = body.getAsJsonObject();
                double sell = number(json, "sell");
                double buy = number(json, "buy");
                // An unknown tag answers with zeros and "available": -1.
                boolean known = sell > 0 || buy > 0 || number(json, "available") >= 0;
                price = new Price(sell, buy, known);
            }
            long now = System.currentTimeMillis();
            if (price == null) {
                // Network trouble: keep a stale price if there is one, and try again later.
                Cached old = PRICES.get(tag);
                price = old != null ? old.price() : new Price(0, 0, false);
                PRICES.put(tag, new Cached(price, now + RETRY_MS));
                return;
            }
            PRICES.put(tag, new Cached(price, now + (price.known() ? PRICE_TTL_MS : RETRY_MS)));
        } catch (RuntimeException e) {
            LOGGER.warn("[Chest Profit] price of {} failed", tag, e);
        } finally {
            IN_FLIGHT.remove(tag);
        }
    }

    private static double number(JsonObject json, String key) {
        try {
            return json.has(key) && !json.get(key).isJsonNull() ? json.get(key).getAsDouble() : 0;
        } catch (RuntimeException e) {
            return 0;
        }
    }

    // ---- Names ----------------------------------------------------------------------------------

    /** True once the name table has arrived. Starts loading it the first time it is asked. */
    public static boolean namesReady() {
        ensureNames();
        return !tagsByName.isEmpty();
    }

    /** The tag of an item by its display name ("Wither Catalyst" → WITHER_CATALYST), or {@code null}. */
    public static String tagFor(String displayName) {
        ensureNames();
        return tagsByName.get(displayName.toLowerCase(Locale.ROOT).trim());
    }

    /** Whether Coflnet lists this tag at all. */
    public static boolean isTag(String tag) {
        ensureNames();
        return knownTags.contains(tag);
    }

    private static void ensureNames() {
        boolean fresh = !tagsByName.isEmpty()
                && System.currentTimeMillis() - namesLoadedAt < NAMES_TTL_MS;
        if (fresh || !namesLoading.compareAndSet(false, true)) return;
        POOL.execute(() -> {
            try {
                loadNames();
            } finally {
                namesLoading.set(false);
            }
        });
    }

    /**
     * Reads Coflnet's full item list once: about eight thousand names, one request. Some names are
     * shared — the dungeon ("STARRED_") copy of a weapon carries the same name as the plain one —
     * and chest loot is never the dungeon copy, so the plain tag wins.
     */
    private static void loadNames() {
        JsonElement body = CoflnetClient.api("/items");
        if (body == null || !body.isJsonArray()) {
            // Retry on a later lookup rather than in a loop.
            namesLoadedAt = System.currentTimeMillis() - NAMES_TTL_MS + RETRY_MS;
            return;
        }
        Map<String, String> byName = new HashMap<>();
        Set<String> tags = new HashSet<>();
        for (JsonElement element : body.getAsJsonArray()) {
            if (!element.isJsonObject()) continue;
            JsonObject item = element.getAsJsonObject();
            if (!item.has("tag") || item.get("tag").isJsonNull()) continue;
            String tag = item.get("tag").getAsString();
            tags.add(tag);
            if (!item.has("name") || item.get("name").isJsonNull()) continue;
            String name = item.get("name").getAsString().toLowerCase(Locale.ROOT).trim();
            if (name.isEmpty() || name.equals("null")) continue;
            byName.merge(name, tag, MarketPrices::preferred);
        }
        tagsByName = byName;
        knownTags = tags;
        namesLoadedAt = System.currentTimeMillis();
        LOGGER.info("[Chest Profit] {} item names loaded from Coflnet", byName.size());
    }

    private static String preferred(String a, String b) {
        boolean aStarred = a.startsWith("STARRED_");
        boolean bStarred = b.startsWith("STARRED_");
        if (aStarred != bStarred) return aStarred ? b : a;
        return a.length() <= b.length() ? a : b;
    }
}
