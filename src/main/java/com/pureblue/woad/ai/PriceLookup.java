package com.pureblue.woad.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Answers auction-house price questions from Coflnet, without involving the model.
 *
 * <p>The hard part of "cheapest Storm's Helmet with a Celestial Goldor skin" is not understanding
 * the sentence — it is knowing that the filter is called {@code Skin} and its value
 * {@code GOLDOR_CELESTIAL}. Coflnet publishes exactly that, per item, so the vocabulary is closed:
 * we fetch the item's filters and look for their option values in what the player wrote. Nothing is
 * guessed, so nothing can be hallucinated, and it works with any model — or none at all.
 */
public final class PriceLookup {

    private static final Logger LOGGER = LoggerFactory.getLogger("Woad");

    /** How many cheapest listings to check for "can I buy this now" before giving up. */
    private static final int MAX_BIN_CHECKS = 6;

    /** Ceiling on the number of filter combinations queried, so a vague question cannot spiral. */
    private static final int MAX_QUERIES = 8;

    /** Words that make a message a price question, in the languages players use. */
    private static final Pattern PRICE_INTENT = Pattern.compile("(?i)\\b(price|cost|worth|cheapest|"
            + "lowest|bin|prix|combien|co[uû]te|moins\\s+ch[eè]re?)\\b");

    /** Those same words, plus filler, removed before looking for the item name. */
    /** Separates the item from the filters people describe after it. */
    private static final Pattern CONNECTOR = Pattern.compile("(?i)\\b(with|avec|having|qui\\s+a)\\b");

    private static final Pattern NOISE = Pattern.compile("(?i)\\b(price|prices|cost|costs|worth|"
            + "cheapest|lowest|bin|how\\s+much|is|the|a|an|of|for|with|and|on|it|whats|what's|what|"
            + "prix|combien|co[uû]te|co[uû]tent|moins|ch[eè]re?|le|la|les|un|une|des|du|de|avec|et|"
            + "sur|c'est|quel|quelle|est)\\b");

    /**
     * Game words that do not appear in Coflnet's option values. "Wither Impact" is the ability the
     * Implosion scroll grants, so nobody asks for it by the scroll's name.
     */
    private static final Map<String, String[]> ALIASES = Map.of(
            "wither impact", new String[]{"AbilityScroll", "IMPLOSION_SCROLL"},
            "implosion", new String[]{"AbilityScroll", "IMPLOSION_SCROLL"},
            "shadow warp", new String[]{"AbilityScroll", "SHADOW_WARP_SCROLL"},
            "wither shield", new String[]{"AbilityScroll", "WITHER_SHIELD_SCROLL"},
            "recomb", new String[]{"Rarity", "MYTHIC"},
            "recombobulated", new String[]{"Rarity", "MYTHIC"});

    /** What the last lookup found, so "who sells it?" can be answered without guessing. */
    private record Result(String item, long price, String seller, String auction) {}

    private static volatile Result lastResult;

    /** "who sells it", "qui la vend" — a follow-up about the item just looked up. */
    private static final Pattern SELLER_QUESTION = Pattern.compile("(?i)\\b(who\\s+(?:is\\s+)?sell\\w*|"
            + "seller|qui\\s+(?:le|la|l')?\\s*vend\\w*|vendeur)\\b");

    private PriceLookup() {}

    /** True when the message looks like a price question, or a follow-up about the last one. */
    public static boolean isPriceQuestion(String question) {
        return PRICE_INTENT.matcher(question).find() || SELLER_QUESTION.matcher(question).find();
    }

    /**
     * Answers "who sells it?" from the last lookup.
     *
     * <p>Without this the question reached the model, which happily named a player from an older
     * message about a completely different item.
     *
     * @return the answer, or {@code null} when this is not a follow-up
     */
    private static String answerSeller(String text) {
        if (!SELLER_QUESTION.matcher(text).find()) return null;
        Result last = lastResult;
        if (last == null) return "Ask me for a price first, then I can say who is selling it.";
        if (last.seller() == null) {
            return last.item() + ": I only have the lowest BIN (" + coins(last.price())
                    + "), not the seller. Ask for the cheapest one with a filter.";
        }
        String line = last.item() + ": " + coins(last.price()) + " by " + last.seller();
        String withAuction = line + " /viewauction " + last.auction();
        return last.auction() != null && withAuction.length() <= 200 ? withAuction : line;
    }

    /**
     * Looks the price up. Blocking — call it off the client thread.
     *
     * @return the chat line to send, or {@code null} to let the model answer instead
     */
    public static String answer(String question) {
        String text = question.toLowerCase(Locale.ROOT);

        // "who sells it?" carries no item name: it refers to the previous lookup.
        String followUp = answerSeller(text);
        if (followUp != null && CoflnetClient.findItem(
                NOISE.matcher(CONNECTOR.split(text, 2)[0]).replaceAll(" ").trim()) == null) {
            return followUp;
        }

        // "cheapest necron chestplate WITH a celestial skin": everything after the connector
        // describes the filters, so only what precedes it should be searched as an item — otherwise
        // "celestial" and "skin" drag the search onto a skin item.
        String itemPart = CONNECTOR.split(text, 2)[0];
        CoflnetClient.Item item =
                CoflnetClient.findItem(NOISE.matcher(itemPart).replaceAll(" ").trim());
        if (item == null) return null; // no idea what item this is: not a price question after all

        // The item's own words must not be matched as filter values, or "storm helmet with a
        // celestial goldor skin" picks STORM_CELESTIAL on the strength of "storm" alone.
        // Only the first occurrence of each word: "storm helmet ... skin celestial storm" must keep
        // its second "storm" so STORM_CELESTIAL can still be recognised.
        String rest = text;
        for (String word : words(item.name())) {
            if (word.length() > 2) rest = rest.replaceFirst(Pattern.quote(word), " ");
        }

        Map<String, Wanted> filters = matchFilters(item.tag(), rest);
        LOGGER.info("[AI] price lookup: {} ({}) filters={}", item.name(), item.tag(), filters);

        if (filters.isEmpty()) {
            long[] price = CoflnetClient.lowestBin(item.tag());
            if (price == null) {
                lastResult = null;
                return item.name() + ": no BIN on the auction house right now.";
            }
            lastResult = new Result(item.name(), price[0], null, null);
            String line = item.name() + ": " + coins(price[0]) + " lowest BIN";
            if (price[1] > 0) line += " (2nd " + coins(price[1]) + ")";
            return line;
        }

        List<CoflnetClient.Auction> auctions = search(item.tag(), filters);
        if (auctions.isEmpty()) {
            lastResult = null;
            return item.name() + " " + describe(filters) + ": none listed right now.";
        }
        // Cheapest first, then skip running auctions: their price is only the current bid, so they
        // would otherwise win every time with a starting bid of a few hundred coins.
        auctions.sort(java.util.Comparator.comparingLong(CoflnetClient.Auction::price));
        CoflnetClient.Auction cheapest = null;
        boolean buyable = false;
        for (int i = 0; i < auctions.size() && i < MAX_BIN_CHECKS; i++) {
            CoflnetClient.Auction candidate = auctions.get(i);
            if (Boolean.TRUE.equals(CoflnetClient.isBin(candidate.uuid()))) {
                cheapest = candidate;
                buyable = true;
                break;
            }
        }
        if (cheapest == null) cheapest = auctions.get(0); // all bids: report it as such

        String line = item.name() + " " + describe(filters) + ": " + coins(cheapest.price());
        if (cheapest.seller() != null) line += " by " + cheapest.seller();
        line += buyable ? " (" + auctions.size() + " listed)" : " (current bid, no BIN listed)";
        lastResult = new Result(item.name(), cheapest.price(), cheapest.seller(), cheapest.uuid());

        // The auction id lets anyone open it with /viewauction — only worth sending whole.
        String withAuction = line + " /viewauction " + cheapest.uuid();
        return withAuction.length() <= 200 ? withAuction : line;
    }

    /**
     * What the player asked of one filter: how to show it, and every value that satisfies it.
     *
     * <p>Several values are needed because Coflnet lists combinations as single strings: a fully
     * scrolled Hyperion is {@code "IMPLOSION_SCROLL SHADOW_WARP_SCROLL WITHER_SHIELD_SCROLL"}, so
     * "with wither impact" has to accept all four options that include the implosion scroll — asking
     * for {@code IMPLOSION_SCROLL} alone matches only the rare single-scroll blades.
     */
    private record Wanted(String label, List<String> values) {
        @Override
        public String toString() {
            return values.size() == 1 ? values.get(0) : label + "(" + values.size() + " variants)";
        }
    }

    /**
     * Runs one query per combination of accepted values and merges the results.
     *
     * <p>Costs a request per combination, which is why the whole lookup can take a second or two —
     * the alternative is missing most of the matching auctions.
     */
    private static List<CoflnetClient.Auction> search(String tag, Map<String, Wanted> filters) {
        List<Map<String, String>> combinations = new ArrayList<>();
        combinations.add(new LinkedHashMap<>());
        for (Map.Entry<String, Wanted> filter : filters.entrySet()) {
            List<Map<String, String>> expanded = new ArrayList<>();
            for (Map<String, String> base : combinations) {
                for (String value : filter.getValue().values()) {
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
                merged.putIfAbsent(auction.uuid(), auction); // the same listing can match twice
            }
        }
        return new ArrayList<>(merged.values());
    }

    /**
     * Finds which of the item's own filters the message asks for.
     *
     * <p>An option matches when every word of its value appears in the message, so
     * {@code GOLDOR_CELESTIAL} is found in "celestial goldor skin" whatever the word order. Once the
     * asked-for words are known, <em>every</em> option containing them is accepted — that is what
     * makes "wither impact" match a Hyperion carrying all three scrolls.
     */
    private static Map<String, Wanted> matchFilters(String tag, String text) {
        Map<String, Wanted> chosen = new LinkedHashMap<>();
        List<CoflnetClient.Filter> available = CoflnetClient.filters(tag);

        // Game slang first: it names the words to look for, rather than a single exact value.
        Map<String, List<String>> wantedWords = new LinkedHashMap<>();
        for (Map.Entry<String, String[]> alias : ALIASES.entrySet()) {
            if (text.contains(alias.getKey())) {
                wantedWords.putIfAbsent(alias.getValue()[0], words(alias.getValue()[1]));
            }
        }

        for (CoflnetClient.Filter filter : available) {
            List<String> wanted = wantedWords.get(filter.name());
            if (wanted == null) {
                // Keep the most specific option mentioned: "goldor celestial" beats a lone word.
                int bestWords = 0;
                for (String option : filter.options()) {
                    if (isWildcard(option)) continue;
                    List<String> words = words(option);
                    if (words.isEmpty() || words.size() <= bestWords) continue;
                    if (containsAll(text, words)) {
                        wanted = words;
                        bestWords = words.size();
                    }
                }
            }
            if (wanted == null || wanted.isEmpty()) continue;

            // Accept every value that includes what was asked for, not just the exact one.
            List<String> values = new ArrayList<>();
            for (String option : filter.options()) {
                if (isWildcard(option)) continue;
                if (words(option).containsAll(wanted)) values.add(option);
            }
            if (!values.isEmpty()) {
                chosen.put(filter.name(), new Wanted(String.join(" ", wanted).toUpperCase(Locale.ROOT), values));
            }
        }

        matchNumbers(chosen, available, text);
        return chosen;
    }

    /** "Any" and "None" would match nearly every sentence, so they are never picked automatically. */
    private static boolean isWildcard(String option) {
        return option.isBlank() || option.equalsIgnoreCase("Any") || option.equalsIgnoreCase("None");
    }

    private static boolean containsAll(String text, List<String> words) {
        for (String word : words) {
            if (word.length() < 3 || !text.contains(word)) return false;
        }
        return true;
    }

    /**
     * Reads the numbers players attach to dungeon gear: stars, the floor it dropped from, and hot
     * potato books. Each is only applied when the item actually offers that filter.
     */
    private static void matchNumbers(Map<String, Wanted> chosen,
                                     List<CoflnetClient.Filter> available, String text) {
        // "5 master stars" means five master stars on top of the five normal ones.
        Matcher master = Pattern.compile("(\\d{1,2})\\s*master\\s*stars?").matcher(text);
        Matcher stars = Pattern.compile("(\\d{1,2})\\s*(?:stars?|✪)").matcher(text);
        if (master.find()) {
            put(chosen, available, "Stars", Math.min(10, Integer.parseInt(master.group(1)) + 5));
        } else if (stars.find()) {
            put(chosen, available, "Stars", Integer.parseInt(stars.group(1)));
        }

        Matcher floor = Pattern.compile("\\b(?:floor|étage|etage|f)\\s*(\\d{1,2})\\b").matcher(text);
        if (floor.find()) {
            put(chosen, available, "ItemTier", Integer.parseInt(floor.group(1)));
        }

        // Players write two different things as "x/y": hot potato books ("10/10") and the dungeon
        // item quality ("50/50", the stat boost). The denominator says which one it is.
        Matcher ratio = Pattern.compile("\\b(\\d{1,2})\\s*/\\s*(\\d{1,2})\\b").matcher(text);
        while (ratio.find()) {
            int value = Integer.parseInt(ratio.group(1));
            int outOf = Integer.parseInt(ratio.group(2));
            if (outOf == 50) {
                put(chosen, available, "BaseStatBoost", value);
            } else if (outOf <= 15) {
                put(chosen, available, "HotPotatoCount", value);
            }
        }

        // Same thing spelled out: "quality 50", "qualité 50".
        Matcher quality = Pattern.compile("\\b(?:quality|qualit[ée])\\s*(\\d{1,2})\\b").matcher(text);
        if (quality.find() && !chosen.containsKey("BaseStatBoost")) {
            put(chosen, available, "BaseStatBoost", Integer.parseInt(quality.group(1)));
        }
    }

    /** Applies a numeric filter only if this item declares it, and only within its allowed range. */
    private static void put(Map<String, Wanted> chosen, List<CoflnetClient.Filter> available,
                            String name, int value) {
        for (CoflnetClient.Filter filter : available) {
            if (!filter.name().equals(name)) continue;
            int max = 0;
            for (String option : filter.options()) {
                try {
                    max = Math.max(max, Integer.parseInt(option.trim()));
                } catch (NumberFormatException ignored) {
                    // "Any"/"None" and the like: not a bound
                }
            }
            if (max > 0 && value > max) return; // out of range: Coflnet would reject the request
            chosen.put(name, new Wanted(Integer.toString(value), List.of(Integer.toString(value))));
            return;
        }
    }

    /** Splits an option value like {@code GOLDOR_CELESTIAL} into its lowercase words. */
    private static List<String> words(String option) {
        List<String> words = new ArrayList<>();
        for (String word : option.toLowerCase(Locale.ROOT).split("[^a-z0-9]+")) {
            if (!word.isBlank()) words.add(word);
        }
        return words;
    }

    /** Renders the applied filters for the chat line, e.g. {@code [Skin GOLDOR_CELESTIAL]}. */
    private static String describe(Map<String, Wanted> filters) {
        StringBuilder out = new StringBuilder("[");
        for (Map.Entry<String, Wanted> filter : filters.entrySet()) {
            if (out.length() > 1) out.append(", ");
            out.append(filter.getKey()).append(' ').append(filter.getValue().label());
        }
        return out.append(']').toString();
    }

    /** 494500000 -> "494.5M". Chat has no room for raw coin counts. */
    public static String coins(long amount) {
        if (amount >= 1_000_000_000L) return trim(amount / 1_000_000_000.0) + "B";
        if (amount >= 1_000_000L) return trim(amount / 1_000_000.0) + "M";
        if (amount >= 1_000L) return trim(amount / 1_000.0) + "k";
        return Long.toString(amount);
    }

    private static String trim(double value) {
        String text = String.format(Locale.ROOT, "%.1f", value);
        return text.endsWith(".0") ? text.substring(0, text.length() - 2) : text;
    }
}
