package com.pureblue.woad.blackjack.chat;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses Hypixel party- and guild-chat lines, e.g. {@code "Party > [MVP+] Username: message"} or
 * {@code "Guild > [MVP+] Username [Member]: message"}.
 *
 * <p>The rank tag before the name and the guild-rank tag after it are both optional, so the pattern
 * skips any number of {@code [...]} groups on either side of the username. Color codes are already
 * stripped upstream (see {@link ChatInterceptor}).
 */
public final class PartyChatParser {

    // "(Party|Guild) > ", optional "[...]" tags, a 1-16 char MC username, optional "[...]" tags, ": body".
    private static final Pattern CHAT_LINE = Pattern.compile(
            "^(Party|Guild)\\s*>\\s*(?:\\[[^\\]]*\\]\\s*)*([A-Za-z0-9_]{1,16})\\s*(?:\\[[^\\]]*\\]\\s*)*:\\s*(.*)$");

    private PartyChatParser() {}

    /** A parsed chat message: which channel it came from, who sent it, and the trimmed body. */
    public record PartyMessage(ChatChannel channel, String sender, String body) {}

    /** Returns the parsed message, or {@code null} if the line is not a party/guild chat message. */
    public static PartyMessage parse(String line) {
        if (line == null) return null;
        Matcher matcher = CHAT_LINE.matcher(line);
        if (!matcher.matches()) return null;
        ChatChannel channel = ChatChannel.fromLabel(matcher.group(1));
        if (channel == null) return null;
        return new PartyMessage(channel, matcher.group(2), matcher.group(3).trim());
    }
}
