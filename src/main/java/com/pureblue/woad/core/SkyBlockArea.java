package com.pureblue.woad.core;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

import java.util.ArrayList;
import java.util.List;

/**
 * Where the player is in SkyBlock, read from what Hypixel shows: the sidebar and the tab list.
 *
 * <p>Neither is plain text. A sidebar line is a scoreboard entry whose visible text is split across
 * its team's prefix and suffix, with the entry's own name — an emoji and colour codes Hypixel puts
 * there to keep lines unique — sitting in between, sometimes in the middle of a word ("The
 * Catac🎂ombs"). So a line is rebuilt from its team, then everything that is not plain text is
 * dropped before it is read. During a run both sources say so: "⏣ The Catacombs (F7)" on the
 * sidebar, "Dungeon: Catacombs" in the tab list; either one is enough.
 *
 * <p>Things that ask (entity rendering) ask every frame, so the answer is refreshed once a second.
 */
public final class SkyBlockArea {

    private static final int REFRESH_TICKS = 20;

    private static boolean inCatacombs;
    private static int ticksUntilRefresh;

    private SkyBlockArea() {}

    /** True during a dungeon run in the Catacombs. */
    public static boolean inCatacombs() {
        return inCatacombs;
    }

    /** Called every client tick. */
    public static void tick(Minecraft mc) {
        if (--ticksUntilRefresh > 0) return;
        ticksUntilRefresh = REFRESH_TICKS;
        inCatacombs = detectCatacombs(mc);
    }

    private static boolean detectCatacombs(Minecraft mc) {
        if (mc.level == null) return false;
        for (String line : sidebarLines(mc)) {
            if (line.contains("The Catacombs")) return true;
        }
        for (String line : tabLines(mc)) {
            if (line.contains("Dungeon: Catacombs")) return true;
        }
        return false;
    }

    /** The sidebar, one cleaned line per entry. */
    public static List<String> sidebarLines(Minecraft mc) {
        List<String> lines = new ArrayList<>();
        if (mc.level == null) return lines;
        Scoreboard scoreboard = mc.level.getScoreboard();
        Objective sidebar = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);
        if (sidebar == null) return lines;
        for (PlayerScoreEntry entry : scoreboard.listPlayerScores(sidebar)) {
            String owner = entry.owner();
            PlayerTeam team = scoreboard.getPlayersTeam(owner);
            // As the game draws it: the entry's name (or its display override) wrapped by the team.
            Component line = PlayerTeam.formatNameForTeam(team, entry.ownerName());
            String clean = clean(line.getString());
            if (!clean.isEmpty()) lines.add(clean);
        }
        return lines;
    }

    /** The tab list's entries, cleaned. */
    public static List<String> tabLines(Minecraft mc) {
        List<String> lines = new ArrayList<>();
        ClientPacketListener connection = mc.getConnection();
        if (connection == null) return lines;
        for (PlayerInfo info : connection.getListedOnlinePlayers()) {
            Component name = info.getTabListDisplayName();
            if (name == null) continue;
            String clean = clean(name.getString());
            if (!clean.isEmpty()) lines.add(clean);
        }
        return lines;
    }

    /**
     * Plain text only: colour codes, emojis and the other symbols Hypixel slips into lines go, and
     * runs of spaces become one.
     */
    static String clean(String text) {
        String stripped = ChatFormatting.stripFormatting(text);
        if (stripped == null) return "";
        return stripped.replaceAll("[^A-Za-z0-9 ()':./,-]", "").replaceAll("\\s+", " ").trim();
    }
}
