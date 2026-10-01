package com.pureblue.woad.ai;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The Coflnet tools the AI can call to answer market questions itself.
 *
 * <p>Instead of the mod guessing what a sentence means from keywords, the model reads the question,
 * looks the item up, reads which filters that item accepts, and searches with exactly the ones the
 * player described — in whatever words or language they used. The structured tools cover the usual
 * path; {@code coflnet_api} reaches the rest of Coflnet's public API (price history, recent sales,
 * recipes, mayors…) for questions nothing else answers.
 */
public final class SkyblockTools {

    /** Listings checked one by one for Buy-It-Now before giving up. Each check is a request. */
    private static final int MAX_BIN_CHECKS = 8;
    /** Filter value combinations searched at most, so a broad request cannot fan out forever. */
    private static final int MAX_QUERIES = 8;
    /** Longest tool result handed to the model; longer ones are cut, to keep requests cheap. */
    private static final int MAX_RESULT = 4000;

    private SkyblockTools() {}

    /** Every market tool, in the order the model should usually reach for them. */
    public static List<AiTool> all() {
        return List.of(findItem(), quickPrice(), itemFilters(), searchAuctions(), lowestBin(),
                bazaarPrice(), coflnetApi());
    }

    // ---- Tools -------------------------------------------------------------------------------

    private static AiTool findItem() {
        JsonObject schema = AiTool.newSchema();
        AiTool.prop(schema, "name", "string",
                "Item name in English, as the player would write it: \"hyperion\", \"necron chestplate\", "
                        + "\"sharpness 7 book\", \"enchanted diamond\". Translate it if the player used another language.");
        AiTool.require(schema, "name");
        return new AiTool("find_item",
                "Finds SkyBlock items by name. Returns the best matches with their tag, which every other "
                        + "market tool needs, and whether they trade on the auction house or the bazaar. "
                        + "Pick the match the player meant; try a shorter or official name if none fits.",
                schema, args -> {
                    String name = AiTool.string(args, "name");
                    if (name.isEmpty()) return "Error: name is empty";
                    List<CoflnetClient.Item> items = CoflnetClient.rankItems(name);
                    if (items.isEmpty()) return "No item matches \"" + name + "\". Try the official English name or fewer words.";
                    StringBuilder out = new StringBuilder();
                    for (int i = 0; i < items.size() && i < 8; i++) {
                        CoflnetClient.Item item = items.get(i);
                        out.append(item.name()).append(" | tag ").append(item.tag()).append(" | ")
                                .append(item.bazaar() ? "bazaar" : "auction house").append('\n');
                    }
                    return out.toString().trim();
                });
    }

    private static AiTool quickPrice() {
        JsonObject schema = AiTool.newSchema();
        AiTool.prop(schema, "request", "string",
                "The price request in short English, item first then its attributes: "
                        + "\"hyperion with wither impact\", \"necron chestplate 5 stars recomb\", \"sharpness 7 book\".");
        AiTool.require(schema, "request");
        return new AiTool("quick_price",
                "One-step price check from a short English description. Fast and good for simple requests "
                        + "(an item alone, or with common attributes). Check which filters it says it applied: "
                        + "if it missed part of the request, use get_item_filters and search_auctions instead.",
                schema, args -> {
                    String request = AiTool.string(args, "request");
                    String answer = request.isEmpty() ? null : PriceLookup.answer(request);
                    return answer != null ? answer
                            : "Could not identify the item in \"" + request + "\". Use find_item first.";
                });
    }

    private static AiTool itemFilters() {
        JsonObject schema = AiTool.newSchema();
        AiTool.prop(schema, "tag", "string", "Item tag from find_item, e.g. HYPERION.");
        AiTool.require(schema, "tag");
        return new AiTool("get_item_filters",
                "Lists the filters the auction house accepts for one item (reforge, stars, enchantments, skins, "
                        + "scrolls, pet level…) with their allowed values. Use the exact names and values it "
                        + "returns in search_auctions.",
                schema, args -> {
                    String tag = tag(args);
                    if (tag.isEmpty()) return "Error: tag is empty";
                    List<CoflnetClient.Filter> filters = CoflnetClient.filters(tag);
                    if (filters.isEmpty()) return "No filters known for " + tag + " (wrong tag, or a bazaar item).";
                    StringBuilder out = new StringBuilder("Filters for " + tag + ":\n");
                    for (CoflnetClient.Filter filter : filters) {
                        if (PriceLookup.AUCTION_META.contains(filter.name())) continue;
                        if (filter.kind() == CoflnetClient.Kind.OTHER) continue;
                        out.append("- ").append(filter.name()).append(": ").append(describe(filter)).append('\n');
                    }
                    return cap(out.toString().trim());
                });
    }

    private static AiTool searchAuctions() {
        JsonObject schema = AiTool.newSchema();
        AiTool.prop(schema, "tag", "string", "Item tag from find_item.");
        AiTool.prop(schema, "filters", "object",
                "Filter name -> wanted value, names and values exactly as get_item_filters lists them, e.g. "
                        + "{\"Stars\": \"5\", \"Recombobulated\": \"true\", \"sharpness\": \"7\"}. A value may also be a "
                        + "list of acceptable values, e.g. every AbilityScroll combination that contains "
                        + "IMPLOSION_SCROLL. Omit for no filter.");
        AiTool.require(schema, "tag");
        return new AiTool("search_auctions",
                "Searches active auction-house listings of an item, filtered, and returns the cheapest "
                        + "Buy-It-Now ones with their seller and auction id (open with /viewauction <id>).",
                schema, SkyblockTools::runSearch);
    }

    private static AiTool lowestBin() {
        JsonObject schema = AiTool.newSchema();
        AiTool.prop(schema, "tag", "string", "Item tag from find_item.");
        AiTool.require(schema, "tag");
        return new AiTool("get_lowest_bin",
                "Lowest and second-lowest Buy-It-Now price of an item on the auction house, without filters.",
                schema, args -> {
                    String tag = tag(args);
                    long[] price = CoflnetClient.lowestBin(tag);
                    if (price == null) return "No BIN listed for " + tag + " right now (or it is a bazaar item).";
                    return tag + ": lowest BIN " + coins(price[0])
                            + (price[1] > 0 ? ", second lowest " + coins(price[1]) : "");
                });
    }

    private static AiTool bazaarPrice() {
        JsonObject schema = AiTool.newSchema();
        AiTool.prop(schema, "tag", "string", "Item tag from find_item.");
        AiTool.require(schema, "tag");
        return new AiTool("get_bazaar_price",
                "Bazaar prices of one unit: instant buy and instant sell. For materials, enchanted books "
                        + "and other bazaar items.",
                schema, args -> {
                    String tag = tag(args);
                    CoflnetClient.Bazaar bazaar = CoflnetClient.bazaar(tag);
                    if (bazaar == null) return tag + " is not sold on the bazaar.";
                    return tag + " (bazaar, per unit): instant buy " + coins(bazaar.buyPrice())
                            + ", instant sell " + coins(bazaar.sellPrice());
                });
    }

    /**
     * Read access to the rest of Coflnet's public API, for questions the other tools do not cover.
     * Only paths under the public sections are allowed — never account, payment or token endpoints.
     */
    private static AiTool coflnetApi() {
        JsonObject schema = AiTool.newSchema();
        AiTool.prop(schema, "path", "string",
                "Path after https://sky.coflnet.com/api, with any query string, e.g. \"/item/price/HYPERION/history/week\".");
        AiTool.require(schema, "path");
        return new AiTool("coflnet_api",
                "Reads any public endpoint of the Coflnet SkyBlock API (GET, JSON, long results are cut). "
                        + "Useful ones, {tag} from find_item:\n"
                        + "/item/price/{tag} - min/median/mean/max and volume sold over the last 2 days\n"
                        + "/item/price/{tag}/current - current buy/sell price\n"
                        + "/item/price/{tag}/history/day | week | month - price history\n"
                        + "/auctions/tag/{tag}/recent/overview - recently sold auctions (price, seller, end)\n"
                        + "/bazaar/{tag}/history/day | week - bazaar price history\n"
                        + "/craft/recipe/{tag} - crafting recipe\n"
                        + "/item/{tag}/details - rarity, category, NPC sell price\n"
                        + "/search/player/{name} - player uuid, then /player/{uuid}/auctions - their last auctions\n"
                        + "/mayor/{year} - mayor election of a SkyBlock year\n"
                        + "/prices/change?itemTags={tag} - price change over the last month",
                schema, args -> {
                    String path = AiTool.string(args, "path");
                    if (!path.startsWith("/")) path = "/" + path;
                    if (path.startsWith("/api/")) path = path.substring(4);
                    if (!allowedPath(path)) {
                        return "Error: only the public item, auction, bazaar, craft, search, player and mayor "
                                + "endpoints can be read.";
                    }
                    JsonElement body = CoflnetClient.api(path);
                    if (body == null) return "No data (HTTP error or unknown path): " + path;
                    return cap(compact(body, 0).toString());
                });
    }

    private static final List<String> ALLOWED_PATHS = List.of("/item/", "/items/", "/auctions/tag/",
            "/auction/", "/bazaar/", "/craft/recipe/", "/search/", "/player/", "/mayor", "/prices/",
            "/filter/options", "/kat/", "/flip/");

    private static boolean allowedPath(String path) {
        if (path.contains("..") || path.contains("://") || path.contains("@")) return false;
        String lower = path.toLowerCase(Locale.ROOT);
        if (lower.contains("/export") || lower.contains("/batch")) return false; // token-only
        for (String prefix : ALLOWED_PATHS) {
            if (lower.startsWith(prefix)) return true;
        }
        return false;
    }

    // ---- Auction search ----------------------------------------------------------------------

    private static String runSearch(JsonObject args) {
        String tag = tag(args);
        if (tag.isEmpty()) return "Error: tag is empty";

        // Names are checked against what the item really accepts — Coflnet answers an unknown filter
        // with an error, which would otherwise look like "nothing listed".
        Map<String, List<String>> wanted = new LinkedHashMap<>();
        if (args.has("filters") && args.get("filters").isJsonObject()) {
            List<CoflnetClient.Filter> available = CoflnetClient.filters(tag);
            for (Map.Entry<String, JsonElement> entry : args.getAsJsonObject("filters").entrySet()) {
                CoflnetClient.Filter filter = byName(available, entry.getKey());
                if (filter == null) {
                    return "Error: " + tag + " has no filter called \"" + entry.getKey()
                            + "\". Call get_item_filters for the exact names.";
                }
                List<String> values = values(entry.getValue());
                if (!values.isEmpty()) wanted.put(filter.name(), values);
            }
        }

        List<Map<String, String>> combinations = new ArrayList<>();
        combinations.add(new LinkedHashMap<>());
        for (Map.Entry<String, List<String>> filter : wanted.entrySet()) {
            List<Map<String, String>> expanded = new ArrayList<>();
            for (Map<String, String> base : combinations) {
                for (String value : filter.getValue()) {
                    if (expanded.size() >= MAX_QUERIES) break;
                    Map<String, String> copy = new LinkedHashMap<>(base);
                    copy.put(filter.getKey(), value);
                    expanded.add(copy);
                }
            }
            combinations = expanded;
        }

        Map<String, CoflnetClient.Auction> merged = new LinkedHashMap<>();
        for (Map<String, String> combination : combinations) {
            for (CoflnetClient.Auction auction : CoflnetClient.listings(tag, combination)) {
                merged.putIfAbsent(auction.uuid(), auction);
            }
        }
        String what = tag + (wanted.isEmpty() ? "" : " " + wanted);
        if (merged.isEmpty()) return what + ": nothing listed right now.";

        // Cheapest first, then only Buy-It-Now: a running auction's price is just its current bid.
        List<CoflnetClient.Auction> auctions = new ArrayList<>(merged.values());
        auctions.sort(Comparator.comparingLong(CoflnetClient.Auction::price));
        List<CoflnetClient.Auction> bins = new ArrayList<>();
        int bids = 0;
        for (int i = 0; i < auctions.size() && i < MAX_BIN_CHECKS && bins.size() < 3; i++) {
            CoflnetClient.Auction candidate = auctions.get(i);
            if (Boolean.FALSE.equals(CoflnetClient.isBin(candidate.uuid()))) {
                bids++;
            } else {
                bins.add(candidate);
            }
        }

        StringBuilder out = new StringBuilder(what + ": " + auctions.size() + " listed.");
        if (bins.isEmpty()) {
            out.append(" No Buy-It-Now among the cheapest; lowest current bid ").append(coins(auctions.get(0).price()));
            return out.toString();
        }
        out.append(" Cheapest Buy-It-Now:");
        for (CoflnetClient.Auction bin : bins) {
            out.append("\n- ").append(coins(bin.price()));
            if (bin.seller() != null) out.append(" by ").append(bin.seller());
            out.append(" (auction id ").append(bin.uuid()).append(')');
        }
        if (bids > 0) out.append("\n(").append(bids).append(" cheaper running auctions skipped: bids, not BIN)");
        return out.toString();
    }

    private static CoflnetClient.Filter byName(List<CoflnetClient.Filter> filters, String name) {
        for (CoflnetClient.Filter filter : filters) {
            if (filter.name().equalsIgnoreCase(name.trim())) return filter;
        }
        return null;
    }

    /** A filter value as the model sent it: one value or a list, numbers and booleans as text. */
    private static List<String> values(JsonElement element) {
        List<String> values = new ArrayList<>();
        if (element == null || element.isJsonNull()) return values;
        if (element.isJsonArray()) {
            for (JsonElement item : element.getAsJsonArray()) values.addAll(values(item));
        } else if (element.isJsonPrimitive()) {
            JsonPrimitive primitive = element.getAsJsonPrimitive();
            String value = primitive.isNumber() ? Long.toString(primitive.getAsLong()) : primitive.getAsString().trim();
            if (!value.isEmpty()) values.add(value);
        }
        return values;
    }

    // ---- Formatting --------------------------------------------------------------------------

    private static String describe(CoflnetClient.Filter filter) {
        switch (filter.kind()) {
            case NUMBER -> {
                List<String> bounds = filter.options();
                return bounds.size() >= 2 ? "number " + bounds.get(0) + "-" + bounds.get(1) : "number";
            }
            case BOOLEAN -> {
                return filter.options().contains("yes") ? "yes / no" : "true / false";
            }
            default -> {
                // Some filters list every combination (gem slots alone run to dozens of long
                // strings); a bounded line per filter keeps the ones after it from being cut off.
                List<String> options = filter.options();
                StringBuilder list = new StringBuilder();
                int shown = 0;
                for (String option : options) {
                    if (shown > 0 && list.length() + option.length() > 300) break;
                    if (shown > 0) list.append(", ");
                    list.append(option);
                    shown++;
                }
                return shown < options.size() ? list + " (+" + (options.size() - shown) + " more)" : list.toString();
            }
        }
    }

    /**
     * Shrinks an API answer to what a model can use: the first entries of long lists, short
     * strings, and no deep nesting. Coflnet's sold-auction list alone is close to a megabyte.
     */
    private static JsonElement compact(JsonElement element, int depth) {
        if (element.isJsonArray()) {
            JsonArray in = element.getAsJsonArray();
            JsonArray out = new JsonArray();
            int limit = depth == 0 ? 15 : 6;
            for (int i = 0; i < in.size() && i < limit; i++) out.add(compact(in.get(i), depth + 1));
            if (in.size() > limit) out.add("… " + (in.size() - limit) + " more");
            return out;
        }
        if (element.isJsonObject()) {
            if (depth >= 4) return new JsonPrimitive("{…}");
            JsonObject out = new JsonObject();
            for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
                if (entry.getValue().isJsonNull()) continue;
                // Raw NBT and icon links are noise for a chat answer.
                String key = entry.getKey().toLowerCase(Locale.ROOT);
                if (key.contains("nbt") || key.contains("icon") || key.equals("texture")) continue;
                out.add(entry.getKey(), compact(entry.getValue(), depth + 1));
            }
            return out;
        }
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            String text = element.getAsString();
            return text.length() > 120 ? new JsonPrimitive(text.substring(0, 120) + "…") : element;
        }
        return element;
    }

    private static String cap(String text) {
        return text.length() <= MAX_RESULT ? text : text.substring(0, MAX_RESULT) + " … (cut)";
    }

    /** "494.5M (494500000)": short for the chat line, exact so the model never has to round. */
    private static String coins(long amount) {
        String shortForm = PriceLookup.coins(amount);
        return shortForm.equals(Long.toString(amount)) ? shortForm : shortForm + " (" + amount + ")";
    }

    private static String tag(JsonObject args) {
        return AiTool.string(args, "tag").toUpperCase(Locale.ROOT).replace(' ', '_');
    }
}
