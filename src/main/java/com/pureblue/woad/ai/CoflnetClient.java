package com.pureblue.woad.ai;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Read-only client for Coflnet's public SkyBlock API ({@code sky.coflnet.com}). No key needed.
 *
 * <p>Everything here runs off the client thread and returns plain data; the caller marshals the
 * result back. Nothing is cached: every question asks Coflnet again, so prices are never stale.
 */
public final class CoflnetClient {

    private static final String BASE = "https://sky.coflnet.com/api";

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .build();

    private CoflnetClient() {}

    /** An item as Coflnet knows it: display name and the internal tag used by every other call. */
    public record Item(String name, String tag) {}

    /** One filter the auction house accepts for an item, with the values it allows. */
    public record Filter(String name, List<String> options) {}

    /** One active listing: its asking price, who put it up, and the id for {@code /viewauction}. */
    public record Auction(long price, String seller, String uuid) {}

    /**
     * Resolves a written name to an item. The search only matches short terms well, so longer
     * phrases are retried with their trailing words dropped ("storm helmet celestial" -> "storm").
     *
     * @return the best match, or {@code null} when nothing resembles the text
     */
    public static Item findItem(String text) {
        String lower = text.toLowerCase(java.util.Locale.ROOT);
        String[] words = lower.trim().split("\\s+");

        // Coflnet's search is fuzzy and returns only a handful of hits, so one query is not enough:
        // "necron chestplate" finds nothing and "necron" alone misses the chestplate. Querying the
        // phrase and each significant word, then scoring the pooled results, finds it.
        java.util.LinkedHashSet<String> terms = new java.util.LinkedHashSet<>();
        for (int len = Math.min(words.length, 4); len >= 1; len--) {
            terms.add(String.join(" ", java.util.Arrays.copyOfRange(words, 0, len)));
        }
        // Dungeon armour is listed possessively ("Necron's Chestplate") and the search wants the
        // apostrophe, so offer that spelling too when the player omits it.
        if (words.length >= 2 && !words[0].contains("'")) {
            terms.add(words[0] + "'s " + String.join(" ",
                    java.util.Arrays.copyOfRange(words, 1, Math.min(words.length, 3))));
        }
        for (String word : words) {
            if (word.length() >= 4) terms.add(word);
        }

        Item best = null;
        int bestScore = 0;
        int queries = 0;
        for (String term : terms) {
            if (queries++ >= 6) break; // keep the whole lookup well under a second
            for (Item item : searchItems(term)) {
                int total = 0;
                int matched = 0;
                for (String word : item.name().toLowerCase(java.util.Locale.ROOT).split("[^a-z0-9]+")) {
                    if (word.length() < 3) continue;
                    total++;
                    if (lower.contains(word)) matched++;
                }
                if (matched == 0) continue;
                // Every word of the name found beats a partial match, then the longer name wins.
                int score = matched * 10 + (matched == total ? 5 : 0);
                if (score > bestScore) {
                    bestScore = score;
                    best = item;
                }
            }
            if (bestScore >= 25) break; // a two-word exact match is good enough, stop querying
        }
        return best;
    }

    private static List<Item> searchItems(String term) {
        List<Item> items = new ArrayList<>();
        JsonElement body = get(BASE + "/item/search/" + encode(term));
        if (body == null || !body.isJsonArray()) return items;
        for (JsonElement element : body.getAsJsonArray()) {
            JsonObject entry = element.getAsJsonObject();
            if (!entry.has("id") || !entry.has("name")) continue;
            // "not on ah" entries exist for abilities and the like; they have no auctions.
            String name = entry.get("name").getAsString();
            if (name.toLowerCase(java.util.Locale.ROOT).contains("not on ah")) continue;
            items.add(new Item(name, entry.get("id").getAsString()));
        }
        return items;
    }

    /** The filters this item accepts, straight from Coflnet — names and allowed values. */
    public static List<Filter> filters(String tag) {
        List<Filter> filters = new ArrayList<>();
        JsonElement body = get(BASE + "/filter/options?itemTag=" + encode(tag));
        if (body == null || !body.isJsonArray()) return filters;
        for (JsonElement element : body.getAsJsonArray()) {
            JsonObject entry = element.getAsJsonObject();
            if (!entry.has("name")) continue;
            List<String> options = new ArrayList<>();
            if (entry.has("options") && entry.get("options").isJsonArray()) {
                for (JsonElement option : entry.getAsJsonArray("options")) {
                    options.add(option.getAsString());
                }
            }
            filters.add(new Filter(entry.get("name").getAsString(), options));
        }
        return filters;
    }

    /**
     * Active listings for an item, with Coflnet's own filters applied server-side.
     *
     * <p>Uses {@code /active/overview} rather than {@code /active/bin}: the latter caps out at ten
     * rows and was demonstrably missing cheaper listings (it started a Hyperion at 540M when one was
     * up at 494.5M). The overview is ordered by price and already carries the seller's name.
     */
    public static List<Auction> listings(String tag, Map<String, String> filters) {
        StringBuilder url = new StringBuilder(BASE + "/auctions/tag/" + encode(tag) + "/active/overview");
        char separator = '?';
        for (Map.Entry<String, String> filter : filters.entrySet()) {
            url.append(separator).append(encode(filter.getKey())).append('=').append(encode(filter.getValue()));
            separator = '&';
        }

        List<Auction> auctions = new ArrayList<>();
        JsonElement body = get(url.toString());
        if (body == null || !body.isJsonArray()) return auctions;
        for (JsonElement element : body.getAsJsonArray()) {
            JsonObject entry = element.getAsJsonObject();
            if (!entry.has("price")) continue;
            auctions.add(new Auction(
                    entry.get("price").getAsLong(),
                    entry.has("playerName") ? entry.get("playerName").getAsString() : null,
                    entry.has("uuid") ? entry.get("uuid").getAsString() : ""));
        }
        return auctions;
    }

    /**
     * Whether a listing can be bought outright.
     *
     * <p>The overview mixes BIN listings with running auctions, whose "price" is only the current
     * bid — a 50/50 chestplate showed up at 1.5k that way. Only this call tells them apart.
     *
     * @return true/false, or {@code null} when the auction could not be read
     */
    public static Boolean isBin(String auctionUuid) {
        if (auctionUuid == null || auctionUuid.isBlank()) return null;
        JsonElement body = get(BASE + "/auction/" + encode(auctionUuid));
        if (body == null || !body.isJsonObject()) return null;
        JsonObject json = body.getAsJsonObject();
        return json.has("bin") ? json.get("bin").getAsBoolean() : null;
    }

    /** Lowest and second-lowest BIN for an item, unfiltered. Returns {@code null} if unknown. */
    public static long[] lowestBin(String tag) {
        JsonElement body = get(BASE + "/item/price/" + encode(tag) + "/bin");
        if (body == null || !body.isJsonObject()) return null;
        JsonObject json = body.getAsJsonObject();
        if (!json.has("lowest")) return null;
        long lowest = json.get("lowest").getAsLong();
        long second = json.has("secondLowest") ? json.get("secondLowest").getAsLong() : 0L;
        return lowest <= 0 ? null : new long[]{lowest, second};
    }

    private static JsonElement get(String url) {
        String body = getRaw(url);
        if (body == null) return null;
        try {
            return JsonParser.parseString(body);
        } catch (Exception e) {
            return null;
        }
    }

    private static String getRaw(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(12))
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            HttpResponse<String> response =
                    HTTP.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() / 100 != 2) return null;
            return response.body();
        } catch (Exception e) {
            return null;
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
