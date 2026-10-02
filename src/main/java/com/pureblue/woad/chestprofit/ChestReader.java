package com.pureblue.woad.chestprofit;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads dungeon reward chests out of the game's container screens.
 *
 * <p>Two screens show them. Croesus' run view ("Catacombs - Floor VII") lists every chest of a run
 * as one item, its loot and price written in the lore under "Contents" and "Cost". A single opened
 * chest ("Bedrock Chest") shows the loot as real items instead, with the price on the button that
 * opens it. Both end up as the same {@link Chest}: what is inside, and what it costs.
 */
public final class ChestReader {

    /** The run view's title; long titles get cut off by the game, hence the loose end. */
    private static final Pattern RUN_VIEW = Pattern.compile("^(?:Master (?:Mode )?)?(?:The )?Catacombs - Fl.*$");
    /** A single chest's title. */
    /**
     * A single chest's title. Hypixel names the screen after the chest type alone ("Bedrock"), while
     * the run view calls the same chest "Bedrock Chest"; both are accepted.
     */
    private static final Pattern CHEST_TITLE =
            Pattern.compile("^(?:The )?(Wood|Gold|Diamond|Emerald|Obsidian|Bedrock)(?: Chest)?(?: \\(.*\\))?$");
    /** Croesus' list of runs, "(1/2) Croesus" when it spans pages. */
    private static final Pattern CROESUS = Pattern.compile("^(?:\\(\\d+/\\d+\\) )?Croesus$");

    private static final Pattern BOOK = Pattern.compile("^Enchanted Book \\((.+) ([IVXLC]+|\\d+)\\)$");
    /** A loot line: a name, optionally followed by "x12" (essence, stacked drops). */
    private static final Pattern AMOUNT = Pattern.compile("^(.+?)\\s+x([\\d,]+)$");
    private static final Pattern COINS = Pattern.compile("^([\\d,]+) Coins$");
    /** A lore line opening the price: "Cost" alone, or "Cost: 2,000,000 Coins" on one line. */
    private static final Pattern COST_LINE = Pattern.compile("^Cost(?::\\s*(.*))?$");
    /** A book's lore title when the item carries no data: "Rejuvenate III". */
    private static final Pattern ENCHANT_LEVEL = Pattern.compile("^(.+) ([IVXLC]+|\\d+)$");

    /**
     * One thing in a chest, or paid to open it.
     *
     * @param label   the name as the game writes it
     * @param tag     the SkyBlock tag to price it by, {@code null} when it could not be identified
     * @param count   how many
     * @param essence essence counts only when the player wants it to
     */
    public record Line(String label, String tag, int count, boolean essence) {}

    /**
     * A reward chest.
     *
     * @param name     "Bedrock Chest"
     * @param color    the colour the game gives the chest's name, ARGB
     * @param slot     the container slot showing it in the run view, -1 for an opened chest
     * @param contents the loot
     * @param coins    the coin part of the price
     * @param costItems anything else the price asks for (a Dungeon Chest Key)
     * @param opened   already claimed in this run
     * @param costKnown false when the price could not be read anywhere
     */
    public record Chest(String name, int color, int slot, List<Line> contents, long coins, List<Line> costItems,
                        boolean opened, boolean costKnown) {

        /** Whether opening it asks for a Dungeon Chest Key already, as a second chest does. */
        public boolean needsKey() {
            for (Line line : costItems) {
                if (KEY_TAG.equals(line.tag())) return true;
            }
            return false;
        }
    }

    /** The key a second chest of a run costs, on top of its coins. */
    public static final String KEY_TAG = "DUNGEON_CHEST_KEY";

    /** Where a run stands, as Croesus' list of runs tells it. */
    public enum RunState {
        /** Nothing claimed yet. */
        UNOPENED,
        /** One chest claimed; a Dungeon Chest Key can still open a second. */
        KEY_LEFT,
        /** Nothing left to open. */
        DONE,
        /** Not a run. */
        NONE
    }

    private ChestReader() {}

    public static boolean isRunView(String title) {
        return RUN_VIEW.matcher(title).matches();
    }

    public static boolean isChest(String title) {
        return CHEST_TITLE.matcher(title).matches();
    }

    public static boolean isCroesus(String title) {
        return CROESUS.matcher(title).matches();
    }

    /**
     * Where one run of Croesus' list stands. The game writes it in the run's description: "No
     * chests opened yet!", "Opened Chest: Bedrock Chest" (the key can still open another) or "No
     * more chests to open!" — that last one wins whatever else is written.
     */
    public static RunState runState(ItemStack stack) {
        if (stack.isEmpty()) return RunState.NONE;
        RunState state = RunState.NONE;
        for (String line : lore(stack)) {
            if (line.equalsIgnoreCase("No more chests to open!")) return RunState.DONE;
            if (line.equalsIgnoreCase("No chests opened yet!")) state = RunState.UNOPENED;
            else if (line.startsWith("Opened Chest:") && state == RunState.NONE) state = RunState.KEY_LEFT;
        }
        return state;
    }

    // ---- Run view -------------------------------------------------------------------------------

    /** Every chest of the run, from the items of the run view. */
    public static List<Chest> readRunView(AbstractContainerMenu menu, int containerSlots) {
        List<Chest> chests = new ArrayList<>();
        for (int i = 0; i < containerSlots && i < menu.slots.size(); i++) {
            Slot slot = menu.slots.get(i);
            ItemStack stack = slot.getItem();
            if (stack.isEmpty()) continue;
            List<String> lore = lore(stack);
            int contentsAt = lore.indexOf("Contents");
            int costAt = costLine(lore);
            if (contentsAt < 0 || costAt < contentsAt) continue; // not a chest, or nothing to read

            List<Line> contents = new ArrayList<>();
            for (int l = contentsAt + 1; l < costAt && !lore.get(l).isEmpty(); l++) {
                contents.add(lootLine(lore.get(l)));
            }
            Cost cost = cost(lore, costAt);
            Component name = stack.getHoverName();
            boolean opened = lore.stream().anyMatch(line -> line.equalsIgnoreCase("Already opened!"));
            Chest chest = new Chest(plain(name.getString()), colorOf(name), i, contents, cost.coins, cost.items, opened, true);
            chests.add(chest);
            SEEN.put(chestType(chest.name()), chest);
        }
        return chests;
    }

    /**
     * The chests of the last run view, by name. Opening one of them shows its loot as items and
     * its price on a button, but if the game ever leaves either out, what the run view said about
     * that chest fills the gap.
     */
    private static final java.util.Map<String, Chest> SEEN = new java.util.concurrent.ConcurrentHashMap<>();

    // ---- An opened chest ------------------------------------------------------------------------

    /**
     * The chest whose screen is open: its loot from the items shown, its price from the lore of
     * the button that opens it. Returns {@code null} until that button has arrived.
     */
    public static Chest readOpenChest(Component title, AbstractContainerMenu menu, int containerSlots) {
        List<Line> contents = new ArrayList<>();
        Cost cost = null;
        for (int i = 0; i < containerSlots && i < menu.slots.size(); i++) {
            ItemStack stack = menu.slots.get(i).getItem();
            if (stack.isEmpty()) continue;
            List<String> lore = lore(stack);
            int costAt = costLine(lore);
            if (costAt >= 0) {
                cost = cost(lore, costAt); // the "open" button
                continue;
            }
            Line line = lootItem(stack);
            if (line != null) contents.add(line);
        }

        String name = plain(title.getString());
        if (!name.endsWith("Chest") && CHEST_TITLE.matcher(name).matches()) name = name + " Chest";
        Chest seen = SEEN.get(chestType(name));
        if (contents.isEmpty() && seen != null) contents = seen.contents();
        boolean costKnown = true;
        if (cost == null) {
            if (seen != null) {
                cost = new Cost(seen.coins(), seen.costItems());
            } else {
                cost = new Cost(0, List.of()); // shown as unknown rather than not at all
                costKnown = false;
            }
        }
        if (contents.isEmpty() && !costKnown) return null; // nothing read yet: the items are still arriving
        return new Chest(name, colorOf(title), -1, contents, cost.coins, cost.items, false, costKnown);
    }

    /** "Bedrock Chest", "Bedrock", "The Bedrock Chest" → "Bedrock": the same chest whatever the screen calls it. */
    private static String chestType(String name) {
        Matcher matcher = CHEST_TITLE.matcher(name);
        return matcher.matches() ? matcher.group(1) : name;
    }

    /** Where the price block starts in a lore, or -1. */
    private static int costLine(List<String> lore) {
        for (int l = 0; l < lore.size(); l++) {
            if (COST_LINE.matcher(lore.get(l)).matches()) return l;
        }
        return -1;
    }

    /**
     * A real item in the chest, or {@code null} for decoration (glass panes, buttons). SkyBlock
     * items carry their tag in their data; essence is shown as a plain head named "Wither Essence
     * x103", so it is recognised by name.
     */
    private static Line lootItem(ItemStack stack) {
        String name = plain(stack.getHoverName().getString());
        // The screen's buttons (go back, the Kismet Feather that rerolls the chest) are not loot,
        // even when they carry an item's data.
        for (String line : lore(stack)) {
            if (line.startsWith("Click to") || line.startsWith("Click here")) return null;
        }
        if (name.equals("Go Back") || name.equals("Close") || name.startsWith("Reroll")) return null;
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        CompoundTag tag = data == null ? null : data.copyTag();
        String id = tag == null ? "" : tag.getStringOr("id", "");

        if (id.equals("ENCHANTED_BOOK") && tag.contains("enchantments")) {
            // One enchantment per book in a chest: {"ultimate_last_stand": 2} → ENCHANTMENT_ULTIMATE_LAST_STAND_2
            CompoundTag enchants = tag.getCompoundOrEmpty("enchantments");
            for (String key : enchants.keySet()) {
                int level = enchants.getIntOr(key, 1);
                String bookTag = "ENCHANTMENT_" + key.toUpperCase(Locale.ROOT) + "_" + level;
                return new Line(bookLabel(key, level), bookTag, stack.getCount(), false);
            }
        }
        // Some items share one generic id and are told apart by name only: every shard is
        // ATTRIBUTE_SHARD — which Coflnet lists too, at next to nothing — while "Apex Dragon Shard"
        // trades as SHARD_APEX_DRAGON. So a shard is always known by its name (Coflnet's list maps
        // shard names to their real tags, irregular ones included), and so is any item whose id
        // Coflnet has never heard of.
        Line named = lootLine(name);
        if (named.tag() != null && named.tag().startsWith("SHARD_")) {
            int count = named.count() > 1 ? named.count() : stack.getCount();
            return new Line(named.label(), named.tag(), count, false);
        }
        if (!id.isEmpty() && (MarketPrices.isTag(id) || !MarketPrices.namesReady())) {
            return new Line(name, id, stack.getCount(), id.startsWith("ESSENCE_"));
        }
        if (!id.isEmpty()) {
            Line byName = lootLine(name);
            if (byName.tag() != null) {
                int count = byName.count() > 1 ? byName.count() : stack.getCount();
                return new Line(byName.label(), byName.tag(), count, byName.essence());
            }
            return new Line(name, id, stack.getCount(), false); // shown as unknown ("?")
        }
        if (name.contains("Essence")) {
            Line line = lootLine(name);
            int count = line.count() > 1 ? line.count() : stack.getCount();
            return new Line(line.label(), line.tag(), count, line.essence());
        }
        if (name.equals("Enchanted Book")) {
            List<String> lore = lore(stack);
            Matcher enchant = lore.isEmpty() ? null : ENCHANT_LEVEL.matcher(lore.get(0));
            if (enchant != null && enchant.matches()) {
                String tagged = bookTag(enchant.group(1), level(enchant.group(2)));
                if (tagged != null) return new Line(lore.get(0), tagged, stack.getCount(), false);
            }
            return null;
        }
        // Shown without its data: known by name, or it is decoration (panes, buttons).
        String byName = MarketPrices.tagFor(name);
        return byName == null ? null : new Line(name, byName, stack.getCount(), byName.startsWith("ESSENCE_"));
    }

    // ---- Lines ----------------------------------------------------------------------------------

    /** One loot line of the run view's lore: "Enchanted Book (Combo II)", "Undead Essence x129"… */
    static Line lootLine(String text) {
        Matcher book = BOOK.matcher(text);
        if (book.matches()) {
            return new Line(book.group(1) + " " + book.group(2), bookTag(book.group(1), level(book.group(2))), 1, false);
        }
        String name = text;
        int count = 1;
        Matcher amount = AMOUNT.matcher(text);
        if (amount.matches()) {
            name = amount.group(1);
            count = parseInt(amount.group(2), 1);
        }
        String tag = MarketPrices.tagFor(name);
        return new Line(name, tag, count, tag != null ? tag.startsWith("ESSENCE_") : name.endsWith(" Essence"));
    }

    /**
     * A book's tag. Ultimate enchantments carry an extra word in theirs ("Combo" is
     * ENCHANTMENT_ULTIMATE_COMBO), which the name does not show — so both spellings are tried
     * against Coflnet's list, where only one of them exists.
     */
    private static String bookTag(String enchant, int level) {
        String base = enchant.toUpperCase(Locale.ROOT).replace("'", "").replaceAll("[\\s-]+", "_");
        String plain = "ENCHANTMENT_" + base + "_" + level;
        String ultimate = "ENCHANTMENT_ULTIMATE_" + base + "_" + level;
        if (MarketPrices.isTag(plain)) return plain;
        if (MarketPrices.isTag(ultimate)) return ultimate;
        return null;
    }

    private record Cost(long coins, List<Line> items) {}

    /** The price block under "Cost": coins ("2,000,000 Coins", or "FREE") and any item asked. */
    private static Cost cost(List<String> lore, int costAt) {
        long coins = 0;
        List<Line> items = new ArrayList<>();
        List<String> lines = new ArrayList<>();
        Matcher inline = COST_LINE.matcher(lore.get(costAt));
        if (inline.matches() && inline.group(1) != null && !inline.group(1).isBlank()) lines.add(inline.group(1).trim());
        for (int l = costAt + 1; l < lore.size() && !lore.get(l).isEmpty(); l++) lines.add(lore.get(l));
        for (String text : lines) {
            Matcher matcher = COINS.matcher(text);
            if (matcher.matches()) {
                coins += parseLong(matcher.group(1));
            } else if (!text.equalsIgnoreCase("FREE")) {
                items.add(lootLine(text));
            }
        }
        return new Cost(coins, items);
    }

    // ---- Text helpers ---------------------------------------------------------------------------

    /** The lore as plain lines, formatting removed. */
    static List<String> lore(ItemStack stack) {
        ItemLore lore = stack.get(DataComponents.LORE);
        List<String> lines = new ArrayList<>();
        if (lore == null) return lines;
        for (Component line : lore.lines()) lines.add(plain(line.getString()));
        return lines;
    }

    static String plain(String text) {
        String stripped = ChatFormatting.stripFormatting(text);
        return stripped == null ? "" : stripped.trim();
    }

    /**
     * The colour of the first coloured piece of a name — styled text or, as SkyBlock still often
     * sends, a legacy "§6" code inside the text.
     */
    static int colorOf(Component name) {
        int[] found = {0};
        name.visit((style, text) -> {
            if (text.isBlank()) return Optional.empty();
            if (style.getColor() != null) {
                found[0] = 0xFF000000 | style.getColor().getValue();
                return Optional.of(Boolean.TRUE);
            }
            int code = text.indexOf('§');
            if (code >= 0 && code + 1 < text.length()) {
                ChatFormatting format = ChatFormatting.getByCode(text.charAt(code + 1));
                if (format != null && format.getColor() != null) {
                    found[0] = 0xFF000000 | format.getColor();
                    return Optional.of(Boolean.TRUE);
                }
            }
            return Optional.empty();
        }, Style.EMPTY);
        return found[0] != 0 ? readable(found[0]) : 0xFFE6EDF7;
    }

    /**
     * Lifts a colour that would vanish on the dark panel — the Bedrock Chest's dark grey — while
     * keeping its hue, so every chest name stays readable.
     */
    private static int readable(int argb) {
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;
        float luminance = (0.299f * r + 0.587f * g + 0.114f * b) / 255f;
        if (luminance >= 0.45f) return argb;
        float t = Math.min(1f, (0.55f - luminance) / 0.55f) * 0.7f;
        r += Math.round((255 - r) * t);
        g += Math.round((255 - g) * t);
        b += Math.round((255 - b) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    /**
     * A book's name as players say it: every book in a chest is just "Enchanted Book", which says
     * nothing in a list. {@code ultimate_last_stand}, 2 → "Last Stand II".
     */
    private static String bookLabel(String key, int level) {
        String base = key.startsWith("ultimate_") && !key.equals("ultimate_wise") && !key.equals("ultimate_jerry")
                ? key.substring("ultimate_".length()) : key;
        StringBuilder out = new StringBuilder();
        for (String word : base.split("_")) {
            if (word.isEmpty()) continue;
            if (!out.isEmpty()) out.append(' ');
            out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return out + " " + roman(level);
    }

    private static String roman(int number) {
        if (number <= 0 || number > 39) return Integer.toString(number);
        String[] tens = {"", "X", "XX", "XXX"};
        String[] ones = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX"};
        return tens[number / 10] + ones[number % 10];
    }

    private static int level(String roman) {
        if (roman.chars().allMatch(Character::isDigit)) return parseInt(roman, 1);
        int total = 0;
        int previous = 0;
        for (int i = roman.length() - 1; i >= 0; i--) {
            int value = switch (roman.charAt(i)) {
                case 'I' -> 1;
                case 'V' -> 5;
                case 'X' -> 10;
                case 'L' -> 50;
                case 'C' -> 100;
                default -> 0;
            };
            total += value < previous ? -value : value;
            previous = Math.max(previous, value);
        }
        return Math.max(1, total);
    }

    private static int parseInt(String digits, int fallback) {
        try {
            return Integer.parseInt(digits.replace(",", ""));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static long parseLong(String digits) {
        try {
            return Long.parseLong(digits.replace(",", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
