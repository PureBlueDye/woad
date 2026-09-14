package com.pureblue.woad.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
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
     * Implosion scroll grants, so nobody asks for it by the scroll's name, and nobody says
     * "recombobulated" out loud.
     */
    private static final Map<String, String[]> ALIASES = Map.of(
            "wither impact", new String[]{"AbilityScroll", "IMPLOSION_SCROLL"},
            "implosion", new String[]{"AbilityScroll", "IMPLOSION_SCROLL"},
            "shadow warp", new String[]{"AbilityScroll", "SHADOW_WARP_SCROLL"},
            "wither shield", new String[]{"AbilityScroll", "WITHER_SHIELD_SCROLL"},
            "recomb", new String[]{"Recombobulated", "true"},
            "recombed", new String[]{"Recombobulated", "true"});

    /**
     * Filters that describe the <em>auction</em> rather than the item. They must never be picked up
     * from a sentence: "lowest bin hyperion" contains "bin", and "sold" or a price would otherwise
     * restrict the search to ended auctions and return nothing.
     */
    private static final java.util.Set<String> AUCTION_META = java.util.Set.of(
            "HighestBid", "StartingBid", "PricePerLevel", "Sold", "Bin", "Seller", "UId",
            "ItemNameContains", "Everything", "HasCreationTime",
            "EndBefore", "EndAfter", "ItemCreatedBefore", "ItemCreatedAfter");

    /** Trailing words in a filter's name that players never say: "HotPotatoCount" is "hot potato". */
    private static final java.util.Set<String> FILLER_WORDS =
            java.util.Set.of("count", "amount", "match", "enchant");

    /** "without a skin", "sans recomb" — the filter is named, but the player wants it absent. */
    private static final Pattern NEGATION =
            Pattern.compile("(?i)\\b(?:without|no|not|sans|pas\\s+de|aucun\\w*)\\s*$");

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

        // The item's own words are passed along rather than deleted from the sentence: a filter
        // value may legitimately repeat them (GOLDEN_DRAGON_ANUBIS on a Golden Dragon), so the
        // matcher weighs them instead of losing them — see matchEnum.
        List<String> itemWords = words(item.name());

        // Bazaar products — enchanted books, materials, potato books — have no auctions at all, so
        // there is nothing to filter and the auction endpoints answer with silence.
        if (item.bazaar()) {
            String line = bazaarLine(item);
            if (line != null) return line;
        }

        item = starredVariant(item, text);

        Map<String, Wanted> filters = matchFilters(item.tag(), text, itemWords);
        LOGGER.info("[AI] price lookup: {} ({}) filters={}", item.name(), item.tag(), filters);

        if (filters.isEmpty()) {
            long[] price = CoflnetClient.lowestBin(item.tag());
            if (price == null) {
                // Coflnet does not always mark bazaar products in search results; if auctions know
                // nothing about it, ask the bazaar before declaring there is no price.
                String line = bazaarLine(item);
                if (line != null) return line;
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
     * Switches to the dungeon version of an item when the question is about stars.
     *
     * <p>Dungeonised gear is a separate item in SkyBlock — {@code SHADOW_FURY} and
     * {@code STARRED_SHADOW_FURY} are two tags, and only the second one has a {@code Stars} filter.
     * Asking for "shadow fury with 10 stars" resolves to the plain tag, where the star count simply
     * does not exist, so it was quietly dropped and the answer ignored half the question.
     *
     * @return the starred item when it applies, otherwise the one that was passed in
     */
    private static CoflnetClient.Item starredVariant(CoflnetClient.Item item, String text) {
        if (item.tag().startsWith("STARRED_")) return item;
        int wanted = requestedStars(text);
        if (wanted <= 0) return item;

        // The two tags differ by their ceiling as much as by their existence: a plain Shadow Fury
        // stops at 9 stars, the dungeon one reaches 10, so asking for 10 on the plain tag is out of
        // range and the star count would be silently dropped.
        long plainMax = starsMaximum(item.tag());
        if (plainMax >= wanted) return item;

        String starredTag = "STARRED_" + item.tag();
        return starsMaximum(starredTag) >= wanted
                ? new CoflnetClient.Item(item.name(), starredTag, false)
                : item;
    }

    /** How many stars the question asks for, counting master stars on top of the five normal ones. */
    private static int requestedStars(String text) {
        Matcher master = Pattern.compile("(\\d{1,2})\\s*master\\s*stars?").matcher(text);
        if (master.find()) return Math.min(10, Integer.parseInt(master.group(1)) + 5);
        Matcher stars = Pattern.compile("(\\d{1,2})\\s*(?:stars?|✪)").matcher(text);
        return stars.find() ? Integer.parseInt(stars.group(1)) : 0;
    }

    /** The highest star count a tag accepts, or 0 when it has no {@code Stars} filter at all. */
    private static long starsMaximum(String tag) {
        for (CoflnetClient.Filter filter : CoflnetClient.filters(tag)) {
            if (filter.name().equals("Stars")) return maximumOf(filter);
        }
        return 0;
    }

    /**
     * The chat line for a bazaar product: both sides of the order book.
     *
     * <p>Buy and sell are far apart on the bazaar, so quoting one number would be misleading —
     * "insta-buy 116M / insta-sell 105M" is the answer to "how much is it".
     *
     * @return the line, or {@code null} when this item is not sold on the bazaar
     */
    private static String bazaarLine(CoflnetClient.Item item) {
        CoflnetClient.Bazaar bazaar = CoflnetClient.bazaar(item.tag());
        if (bazaar == null) return null;
        lastResult = new Result(item.name(), bazaar.buyPrice(), null, null);
        return item.name() + " (bazaar): buy " + coins(bazaar.buyPrice())
                + " / sell " + coins(bazaar.sellPrice());
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
    private static Map<String, Wanted> matchFilters(String tag, String text, List<String> itemWords) {
        Map<String, Wanted> chosen = new LinkedHashMap<>();
        List<CoflnetClient.Filter> available = CoflnetClient.filters(tag);

        // "lvl 100" and "level 100" are the same request; normalising once means every filter
        // whose name ends in "Level" is matched by both spellings.
        String normalised = text.replaceAll("\\blvl\\b", "level");

        // Game slang first: it names the words to look for, rather than a single exact value.
        Map<String, List<String>> wantedWords = new LinkedHashMap<>();
        Map<String, String> exactValues = new LinkedHashMap<>();
        Set<String> negated = new java.util.HashSet<>();
        for (Map.Entry<String, String[]> alias : ALIASES.entrySet()) {
            int at = normalised.indexOf(alias.getKey());
            if (at < 0) continue;
            String name = alias.getValue()[0];
            if (NEGATION.matcher(normalised.substring(0, at)).find()) {
                negated.add(name);
                continue;
            }
            wantedWords.putIfAbsent(name, words(alias.getValue()[1]));
            exactValues.putIfAbsent(name, alias.getValue()[1]);
        }

        Set<String> ambiguous = ambiguousShortNames(available);

        // Numbers and booleans are matched by the filter's own name, so they go first and claim the
        // words they used. An enum value that spells the same word then steps aside: "clean" is both
        // a boolean filter and a reforge, and setting both would ask for an item that cannot exist.
        Set<String> claimed = new java.util.HashSet<>();
        for (CoflnetClient.Filter filter : available) {
            if (AUCTION_META.contains(filter.name())) continue;
            switch (kindOf(filter)) {
                case NUMBER -> matchNumber(chosen, filter, normalised, ambiguous, claimed);
                case BOOLEAN -> matchBoolean(chosen, filter, normalised,
                        negated.contains(filter.name()) ? falsy(filter) : exactValues.get(filter.name()),
                        claimed);
                default -> { }
            }
        }
        for (CoflnetClient.Filter filter : available) {
            if (AUCTION_META.contains(filter.name())) continue;
            // "without wither impact" asks to exclude a value, which the auction house cannot
            // express — better to drop the filter than to search for its opposite.
            if (negated.contains(filter.name())) continue;
            if (kindOf(filter) == CoflnetClient.Kind.ENUM) {
                matchEnum(chosen, filter, normalised, wantedWords.get(filter.name()),
                        itemWords, claimed);
            }
        }

        matchNumbers(chosen, available, normalised);
        return chosen;
    }

    /**
     * Short names that more than one numeric filter would answer to.
     *
     * <p>A filter is also matched by the last word of its name, so "level 100" finds
     * {@code PetLevel} without the player writing "pet level". That shortcut is only safe while the
     * word points at one filter: {@code PerfectGemsCount} and {@code FlawlessGemsCount} both end in
     * "gems", so "3 gems" is ambiguous and neither may claim it.
     */
    private static Set<String> ambiguousShortNames(List<CoflnetClient.Filter> available) {
        Map<String, Integer> seen = new LinkedHashMap<>();
        for (CoflnetClient.Filter filter : available) {
            List<String> words = nameWords(filter.name());
            if (words.size() < 2) continue;
            String last = words.get(words.size() - 1);
            seen.merge(last, 1, Integer::sum);
        }
        Set<String> ambiguous = new java.util.HashSet<>();
        seen.forEach((word, count) -> {
            if (count > 1) ambiguous.add(word);
        });
        return ambiguous;
    }

    /**
     * The shape to match this filter by.
     *
     * <p>Coflnet's own type is trusted, with one correction: a handful of yes/no filters are
     * declared as {@code Equal} ({@code IsShiny}, {@code ArtOfTheWar}). Matching those by their
     * option values would make a bare "yes" anywhere in the sentence set them, so they are treated
     * as booleans and matched by the filter's <em>name</em> instead.
     */
    private static CoflnetClient.Kind kindOf(CoflnetClient.Filter filter) {
        if (filter.kind() != CoflnetClient.Kind.ENUM) return filter.kind();
        boolean yesNo = false;
        for (String option : filter.options()) {
            if (isWildcard(option)) continue;
            if (!option.equalsIgnoreCase("yes") && !option.equalsIgnoreCase("no")
                    && !option.equalsIgnoreCase("true") && !option.equalsIgnoreCase("false")) {
                return CoflnetClient.Kind.ENUM;
            }
            yesNo = true;
        }
        return yesNo ? CoflnetClient.Kind.BOOLEAN : CoflnetClient.Kind.ENUM;
    }

    /**
     * Picks a listed value out of the sentence, then widens to every value that contains it.
     *
     * <p>Widening is what makes "with wither impact" match a Hyperion carrying all three scrolls:
     * Coflnet stores combinations as one string, so the exact option {@code IMPLOSION_SCROLL} alone
     * would only find the rare single-scroll blades.
     */
    private static void matchEnum(Map<String, Wanted> chosen, CoflnetClient.Filter filter,
                                  String text, List<String> alias, List<String> itemWords,
                                  Set<String> claimed) {
        List<String> wanted = alias;
        if (wanted == null) {
            // Score an option by how much of it the player actually added on top of the item's own
            // name. "Storm's Helmet with a celestial goldor skin" mentions every word of
            // STORM_CELESTIAL, but "celestial" is the only one the player chose; GOLDOR_CELESTIAL
            // contributes two, so it wins. Requiring at least one such word is also what lets
            // GOLDEN_DRAGON_ANUBIS match "golden dragon with anubis skin" at all.
            int best = 0;
            for (String option : filter.options()) {
                if (isWildcard(option)) continue;
                List<String> words = words(option);
                if (words.isEmpty() || !containsAll(text, words)) continue;
                int added = 0;
                boolean allClaimed = true;
                for (String word : words) {
                    if (itemWords.contains(word)) continue;
                    added++;
                    if (!claimed.contains(word)) allClaimed = false;
                }
                if (added == 0 || allClaimed || added <= best) continue;
                wanted = words;
                best = added;
            }
        }
        if (wanted == null || wanted.isEmpty()) return;

        List<String> values = new ArrayList<>();
        for (String option : filter.options()) {
            if (isWildcard(option)) continue;
            if (words(option).containsAll(wanted)) values.add(option);
        }
        if (!values.isEmpty()) {
            chosen.put(filter.name(), new Wanted(String.join(" ", wanted).toUpperCase(Locale.ROOT), values));
        }
    }

    /**
     * Finds "<name> N" or "N <name>" for a numeric filter, whatever it is called.
     *
     * <p>This is where "any filter" comes from: every enchantment an item can carry is published as
     * a numeric filter ({@code sharpness}, {@code ultimate_wise}, {@code critical}…), alongside the
     * counts ({@code PerfectGemsCount}, {@code UnlockedSlots}, {@code PetLevel}). Reading the name
     * from the API and looking for a number next to it covers all of them at once, instead of the
     * four that used to be hard-coded.
     */
    private static void matchNumber(Map<String, Wanted> chosen, CoflnetClient.Filter filter,
                                    String text, Set<String> ambiguous, Set<String> claimed) {
        for (String phrase : namePhrases(filter.name(), ambiguous)) {
            String pattern = String.join("[\\s_-]*", phrase.split(" "));
            Matcher after = Pattern.compile("\\b" + pattern + "\\s*:?\\s*(\\d{1,3})\\b").matcher(text);
            Matcher before = Pattern.compile("\\b(\\d{1,3})\\s*" + pattern + "\\b").matcher(text);
            int value;
            if (after.find()) {
                value = Integer.parseInt(after.group(1));
            } else if (before.find()) {
                value = Integer.parseInt(before.group(1));
            } else {
                continue;
            }
            if (value > maximumOf(filter)) return; // out of range: Coflnet would reject the request
            chosen.put(filter.name(), new Wanted(Integer.toString(value), List.of(Integer.toString(value))));
            claimed.addAll(words(phrase));
            return;
        }
    }

    /** Sets a yes/no filter when its name is mentioned, respecting "without …". */
    private static void matchBoolean(Map<String, Wanted> chosen, CoflnetClient.Filter filter,
                                     String text, String aliasValue, Set<String> claimed) {
        String value = aliasValue;
        if (value == null) {
            for (String phrase : namePhrases(filter.name(), Set.of())) {
                int at = text.indexOf(phrase);
                if (at < 0) continue;
                boolean negated = NEGATION.matcher(text.substring(0, at)).find();
                value = negated ? falsy(filter) : truthy(filter);
                claimed.addAll(words(phrase));
                break;
            }
        }
        if (value != null) chosen.put(filter.name(), new Wanted(value, List.of(value)));
    }

    /** Coflnet spells booleans either true/false or yes/no, per filter — use what it offers. */
    private static String truthy(CoflnetClient.Filter filter) {
        return filter.options().contains("yes") ? "yes" : "true";
    }

    private static String falsy(CoflnetClient.Filter filter) {
        if (filter.options().contains("no")) return "no";
        return filter.options().contains("false") ? "false" : null;
    }

    /**
     * A numeric filter's options are {@code [minimum, maximum]}, not a list of values.
     *
     * <p>Read as a {@code long}: open-ended filters such as {@code PetLevel} publish a bound of
     * 50000000000, which overflows an int — and a failed parse used to leave the maximum at zero,
     * so every level was rejected as out of range.
     */
    private static long maximumOf(CoflnetClient.Filter filter) {
        long max = 0;
        for (String option : filter.options()) {
            try {
                max = Math.max(max, Long.parseLong(option.trim()));
            } catch (NumberFormatException ignored) {
                // "Any"/"None" and the like: not a bound
            }
        }
        return max;
    }

    /**
     * How a filter's name might be written in a sentence, most specific first.
     *
     * <p>{@code HotPotatoCount} becomes "hot potato count" then "hot potato"; {@code ultimate_wise}
     * becomes "ultimate wise" then "wise", because that is how the ultimate enchantments are asked
     * for. Only ultimates give up their first word: dropping it everywhere would make "gift 3"
     * match {@code divine_gift} and, worse, short names collide.
     */
    private static List<String> namePhrases(String name, Set<String> ambiguous) {
        List<String> words = nameWords(name);
        if (words.isEmpty()) return List.of();

        java.util.LinkedHashSet<String> phrases = new java.util.LinkedHashSet<>();
        phrases.add(String.join(" ", words));
        if (words.size() > 1 && FILLER_WORDS.contains(words.get(words.size() - 1))) {
            phrases.add(String.join(" ", words.subList(0, words.size() - 1)));
        }
        if (words.size() > 1 && words.get(0).equals("ultimate")) {
            phrases.add(String.join(" ", words.subList(1, words.size())));
        }
        // "level 100" for PetLevel: the last word alone, but only where it is unambiguous and long
        // enough to be meant — "Stars" keeps its own idioms, handled in matchNumbers.
        String last = words.get(words.size() - 1);
        if (words.size() > 1 && last.length() >= 4 && !ambiguous.contains(last)
                && !FILLER_WORDS.contains(last)) {
            phrases.add(last);
        }
        return new ArrayList<>(phrases);
    }

    /** A filter name split into lowercase words: {@code HotPotatoCount} -> hot, potato, count. */
    private static List<String> nameWords(String name) {
        return words(name.replaceAll("(?<=[a-z0-9])(?=[A-Z])", " "));
    }

    /** "Any" and "None" would match nearly every sentence, so they are never picked automatically. */
    private static boolean isWildcard(String option) {
        return option.isBlank() || option.equalsIgnoreCase("Any") || option.equalsIgnoreCase("None");
    }

    /**
     * Whether the sentence contains every one of these words, as whole words.
     *
     * <p>Matching on substrings made "sharpness 7" select the reforge {@code Sharp}, which then
     * asked the auction house for a sharp Hyperion with sharpness 7 and found nothing.
     */
    private static boolean containsAll(String text, List<String> words) {
        for (String word : words) {
            if (word.length() < 3) return false;
            if (!Pattern.compile("\\b" + Pattern.quote(word) + "\\b").matcher(text).find()) {
                return false;
            }
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
            long max = maximumOf(filter);
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
