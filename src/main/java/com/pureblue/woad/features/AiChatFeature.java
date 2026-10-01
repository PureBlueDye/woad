package com.pureblue.woad.features;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.pureblue.woad.ai.AiBackend;
import com.pureblue.woad.ai.AiTool;
import com.pureblue.woad.ai.ChatMessage;
import com.pureblue.woad.ai.ClaudeBackend;
import com.pureblue.woad.ai.OllamaBackend;
import com.pureblue.woad.ai.OpenAiBackend;
import com.pureblue.woad.ai.PriceLookup;
import com.pureblue.woad.ai.PromptStore;
import com.pureblue.woad.ai.SkyblockTools;
import com.pureblue.woad.core.Feature;
import com.pureblue.woad.core.setting.BooleanSetting;
import com.pureblue.woad.core.setting.IntSetting;
import com.pureblue.woad.core.setting.ModeSetting;
import com.pureblue.woad.core.setting.StringSetting;
import com.pureblue.woad.ui.Theme;
import com.pureblue.woad.ui.UiButton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;

/**
 * An AI assistant that answers in chat when a player writes the trigger command (e.g. {@code !ai}).
 *
 * <p>It listens only to the channels enabled in the settings (party / guild / all chat), replies in
 * the same channel the question came from, and is told by its system prompt to answer in English
 * and to keep answers short.
 *
 * <p>The model is given tools: it looks prices up on Coflnet itself and runs party commands from a
 * small allow-list (invite, kick, warp, ...) — never arbitrary commands. Understanding the question
 * is its job, so players can ask in their own words. A model that cannot use tools falls back to
 * the mod's own keyword matching.
 *
 * <p>Backends: a local Ollama model, or an API key (Claude / OpenAI). The system prompt is picked
 * from the text files in {@code config/woad/ai_prompts/}.
 */
public class AiChatFeature extends Feature {

    private static final Logger LOGGER = LoggerFactory.getLogger("Woad");

    /** Hypixel chat lines, formatting already stripped: sender + message per channel. */
    private static final Pattern PARTY = Pattern.compile("^Party > (?:\\[[^\\]]+\\] )?(\\w{1,16})(?: \\[[^\\]]+\\])?: (.+)$");
    private static final Pattern GUILD = Pattern.compile("^Guild > (?:\\[[^\\]]+\\] )?(\\w{1,16})(?: \\[[^\\]]+\\])?: (.+)$");
    /**
     * Public / hub chat. SkyBlock stacks several things before the name — level, emblem and rank,
     * e.g. {@code [415] ⚛ [MVP+] Pure_Blue_Dye: hi} — so skip any number of leading bracket groups
     * and symbols rather than a single rank tag.
     */
    private static final Pattern ALL = Pattern.compile(
            "^(?:(?:\\[[^\\]]*\\]|[^\\s\\w:])\\s*)*(\\w{1,16})(?:\\s+\\[[^\\]]+\\])?: (.+)$");

    /**
     * Private messages. Hypixel shows both sides of a whisper: {@code From [MVP+] Player: text} is
     * someone writing to us, {@code To [MVP+] Player: text} is what we sent them.
     *
     * <p>Both matter. "From" is a question to answer; "To" is the other half of the conversation,
     * and it is also how the local player reaches the AI — typing {@code /msg Bob !ai …} should
     * answer Bob, exactly as asking in party chat answers the party. In both cases the name in the
     * line is the person to reply to, which is why the whisper target travels with the message
     * instead of being fixed per channel like {@code /pc} and {@code /gc}.
     */
    private static final Pattern WHISPER_FROM =
            Pattern.compile("^From (?:\\[[^\\]]+\\] )?(\\w{1,16})(?: \\[[^\\]]+\\])?: (.+)$");
    private static final Pattern WHISPER_TO =
            Pattern.compile("^To (?:\\[[^\\]]+\\] )?(\\w{1,16})(?: \\[[^\\]]+\\])?: (.+)$");

    /**
     * The model asks for an action with: {@code [CMD] <action> <player>}. Only the allowed actions
     * are part of the pattern, so a stray "CMD" in a sentence cannot trigger anything, and the
     * brackets / punctuation around the marker are optional — models format it loosely.
     */
    private static final Pattern COMMAND_DIRECTIVE = Pattern.compile(
            "(?i)\\[?CMD]?\\s*[:\\-]?\\s*(?:party[_ ])?(invite|kick|transfer|promote|warp)\\b[\\s:]*(\\w{0,16})");

    /** Minecraft names are 3-16 word characters. */
    private static final Pattern NAME_TOKEN = Pattern.compile("[A-Za-z0-9_]{3,16}");

    /** What the party tool accepts as a player name: a whole Minecraft name and nothing else. */
    private static final Pattern PLAYER_NAME = Pattern.compile("[A-Za-z0-9_]{1,16}");

    /**
     * A question about a word rather than a request to act ("what does invite mean") — those go to
     * the model instead of running a command.
     */
    private static final Pattern QUESTION_ABOUT = Pattern.compile("(?i)^\\s*(?:what|how|why|who|when|"
            + "where|which|explain|define|means?|qu'est|c'est quoi|quoi|comment|pourquoi|explique|"
            + "signifie|que veut)\\b");

    /** Name-shaped words that are really just filler, in the languages players actually use. */
    private static final Set<String> NOT_NAMES = Set.of(
            "the", "and", "for", "you", "can", "please", "party", "from", "into", "with", "this",
            "that", "him", "her", "them", "guy", "dude", "bro", "plz", "pls", "thx", "thanks",
            "someone", "player", "friend", "guild", "chat", "team", "group", "leader", "member",
            "now", "here", "asap", "back", "out", "one", "all", "everyone",
            "merci", "stp", "svp", "mec", "gars", "dans", "une", "les", "des", "est", "peux",
            "peut", "veux", "tu", "moi", "ami", "joueur", "personne", "membre", "equipe", "groupe",
            "quelqu", "quelqu_un", "maintenant", "vite",
            "por", "favor", "puedes", "alguien", "jugador",
            "invite", "inviter", "invita", "invitar", "add", "kick", "kicker", "expulse",
            "expulser", "vire", "virer", "degage", "transfer", "transferer", "promote",
            "promouvoir", "warp");

    /** Markers around the part of the prompt that only applies while commands are allowed. */
    private static final String COMMANDS_OPEN = "[COMMANDS]";
    private static final String COMMANDS_CLOSE = "[/COMMANDS]";

    /** Put in front of every answer so other players can tell a bot is talking. */
    private static final String AI_TAG = "[AI] ";
    /** Hypixel drops overly long chat lines; keep well under the limit (tag included). */
    private static final int MAX_REPLY_LENGTH = 230;
    /** Minimum gap between two answers, to avoid being kicked for spam. */
    private static final long COOLDOWN_MS = 4000L;
    /** Ticks between two lines we send (~1.25s at 20 TPS), to clear Hypixel's command throttle. */
    private static final int TICKS_BETWEEN_SENDS = 25;

    private final ModeSetting backend = addSetting(new ModeSetting("Backend",
            "Where the AI runs: a local Ollama model, or an API key.",
            "Ollama", List.of("Ollama", "Claude", "OpenAI")));

    private final StringSetting trigger = addSetting(new StringSetting("Trigger",
            "Word after \"!\" that calls the AI. \"ai\" means players write !ai <question>.", "ai"));

    /**
     * Which prompt file is used. The choices are read from the folder each time, so a file dropped
     * in shows up on the next click without a restart.
     */
    private final ModeSetting prompt = addSetting(new ModeSetting("Prompt",
            "Which prompt the AI follows. Every .txt file in config/woad/ai_prompts is one prompt: "
                    + "add your own there, then pick it here.",
            PromptStore.DEFAULT_PROMPT, List.of(PromptStore.DEFAULT_PROMPT)) {
        @Override
        public List<String> getOptions() {
            return PromptStore.promptNames(AiChatFeature::defaultPrompt);
        }

        @Override
        public void read(JsonElement element) {
            // Kept even if the file is gone for now: loadPrompt falls back to Default meanwhile.
            if (element != null && element.isJsonPrimitive()) set(element.getAsString());
        }
    });

    private final StringSetting ollamaUrl = addSetting(new StringSetting("Ollama URL",
            "Address of your local Ollama server.", "http://localhost:11434"));

    private final StringSetting ollamaModel = addSetting(new StringSetting("Ollama model",
            "Local model name, e.g. llama3.2 or mistral.", "llama3.2"));

    private final IntSetting keepAlive = addSetting(new IntSetting("Keep model loaded",
            "Minutes Ollama keeps the model in memory between questions. -1 keeps it loaded for "
                    + "good, so answers are always instant; 0 unloads it right away. Ollama's own "
                    + "default is 5.", -1, -1, 1440));

    private final StringSetting apiKey = addSetting(new StringSetting("API key",
            "Key used for Claude or OpenAI. Stored in your config folder.", "", true));

    private final StringSetting apiModel = addSetting(new StringSetting("API model",
            "Model id for the API backend. Leave empty for a small, cheap default "
                    + "(claude-haiku-4-5 / gpt-4o-mini).", ""));

    private final BooleanSetting inParty = addSetting(new BooleanSetting("Party chat",
            "Answer questions asked in party chat.", true));

    private final BooleanSetting inGuild = addSetting(new BooleanSetting("Guild chat",
            "Answer questions asked in guild chat.", false));

    private final BooleanSetting inAll = addSetting(new BooleanSetting("All chat",
            "Answer questions asked in public chat.", false));

    private final BooleanSetting inWhisper = addSetting(new BooleanSetting("Private messages",
            "Answer questions whispered with /msg. The reply is whispered back to that player.",
            true));

    private final BooleanSetting inSolo = addSetting(new BooleanSetting("Solo / other servers",
            "Answer in normal chat outside Hypixel (singleplayer worlds, vanilla servers). "
                    + "Handy for testing.", true));

    private final BooleanSetting allowCommands = addSetting(new BooleanSetting("Allow commands",
            "Let the AI run party commands (invite, kick, transfer, promote, warp) when a player "
                    + "asks, however it is worded.", true));

    private final BooleanSetting priceLookup = addSetting(new BooleanSetting("Auction prices",
            "Let the AI check Coflnet itself: auction and bazaar prices with any filter, recent "
                    + "sales, price history. Ask in your own words.", true));

    private final IntSetting memory = addSetting(new IntSetting("Memory",
            "How many recent chat messages the AI remembers, so a conversation can span several "
                    + "messages. Higher means better memory but slower answers. 0 disables it.",
            100, 0, 500).slider(5, value -> value == 0 ? "Off" : Integer.toString(value)));

    private long lastReplyAt = 0L;
    /** Our own last answer — public chat echoes it back to us, and answering it would loop. */
    private String lastSentReply = "";

    /**
     * Rolling window of what was recently said, oldest first: every player message from an enabled
     * channel plus our own answers. Sent with each request so the AI can hold a conversation
     * instead of seeing each question in isolation.
     */
    private final Deque<ChatMessage> history = new ArrayDeque<>();

    /**
     * A line waiting to be sent: {@code command} carries {@code text} ({@code null} command for
     * plain chat), or is sent alone when {@code text} is {@code null} — a party command.
     */
    private record Outgoing(String command, String text) {}

    /** Lines waiting their turn, so two never leave the client in the same tick. */
    private final Deque<Outgoing> outbox = new ArrayDeque<>();
    private int sendCooldown = 0;

    /**
     * A question waiting to be answered.
     *
     * @param whisperTarget the player to whisper the answer back to, {@code null} outside whispers
     */
    private record Pending(String sender, String question, Channel channel, String whisperTarget) {}

    /** Questions asked faster than the cooldown allows answering them. */
    private final Deque<Pending> inbox = new ArrayDeque<>();

    /** Beyond this many waiting questions it is a flood, not a conversation. */
    private static final int MAX_PENDING = 5;

    /**
     * The backend and model last found unable to use tools, so the next questions skip straight to
     * answering without them instead of being refused once each.
     */
    private String noToolsModel = "";

    public AiChatFeature() {
        super("ai_chat", "AI Chat",
                "Answers in chat when a player writes your trigger command. Works with a local "
                        + "Ollama model or an API key.",
                false);
    }

    // ---- Chat handling ---------------------------------------------------------------------

    @Override
    public void onChatMessage(String message) {
        // Whispers are checked first: "From Bob: hi" would otherwise be swallowed by the public
        // chat pattern, which accepts anything shaped like "<words> Name: text".
        Matcher whisper = WHISPER_FROM.matcher(message);
        boolean incoming = whisper.matches();
        boolean outgoing = false;
        if (!incoming) {
            whisper = WHISPER_TO.matcher(message);
            outgoing = whisper.matches();
        }
        if (incoming || outgoing) {
            if (!channelEnabled(Channel.WHISPER)) return;
            String other = whisper.group(1);
            // The name in the line is the person at the other end either way: the sender of a
            // "From", the recipient of a "To" — and the reply is whispered to them.
            String sender = outgoing ? Minecraft.getInstance().getUser().getName() : other;
            tryAnswer(sender, whisper.group(2).trim(), Channel.WHISPER, other);
            return;
        }

        Channel channel = null;
        Matcher matcher = PARTY.matcher(message);
        if (matcher.matches()) {
            channel = Channel.PARTY;
        } else if ((matcher = GUILD.matcher(message)).matches()) {
            channel = Channel.GUILD;
        } else if ((matcher = ALL.matcher(message)).matches()) {
            channel = Channel.ALL;
        }
        if (channel == null || !channelEnabled(channel)) return;

        tryAnswer(matcher.group(1), matcher.group(2).trim(), channel, null);
    }

    /**
     * Player chat outside Hypixel (singleplayer, vanilla servers). The sender and text arrive
     * already separated, so there is nothing to parse — and the answer goes back as a normal chat
     * message, since {@code /ac} does not exist there.
     */
    @Override
    public void onPlayerChatMessage(String sender, String content) {
        if (!inSolo.enabled() || content == null) return;
        tryAnswer(sender, content.trim(), Channel.DIRECT, null);
    }

    /** Shared path: check the trigger, then queue the question for answering. */
    private void tryAnswer(String sender, String body, Channel channel, String whisperTarget) {
        if (!lastSentReply.isEmpty() && body.equals(lastSentReply)) return; // never answer ourselves

        // Remember everything players say here, not just questions — that is the conversation
        // context the AI needs to follow a discussion.
        remember(ChatMessage.user("[" + channel.label + "] " + sender + ": " + body));

        String prefix = "!" + trigger.get().trim().toLowerCase(Locale.ROOT);
        if (!body.toLowerCase(Locale.ROOT).startsWith(prefix)) return;

        String question = body.substring(prefix.length()).trim();
        if (question.isEmpty()) return;

        // Two questions arriving within the cooldown used to mean the second one was dropped on the
        // floor and never answered. They wait their turn instead, and pump() releases them as the
        // cooldown allows — the delay is about not being kicked for spam, not about ignoring people.
        if (inbox.size() >= MAX_PENDING) {
            LOGGER.warn("[AI] {} questions already waiting, ignoring one from {}", inbox.size(), sender);
            return;
        }
        inbox.add(new Pending(sender, question, channel, whisperTarget));
        pump();
    }

    /**
     * Answers the oldest waiting question, if the spam cooldown has passed.
     *
     * <p>Called when a question arrives and again on every tick, so a queued one still goes out
     * once its turn comes even though nothing else happens in chat.
     */
    private void pump() {
        if (inbox.isEmpty()) return;
        long now = System.currentTimeMillis();
        if (now - lastReplyAt < COOLDOWN_MS) return;
        lastReplyAt = now;
        answerNow(inbox.poll());
    }

    /** Runs one question: the model, with the tools the settings allow. */
    private void answerNow(Pending pending) {
        // The question we are about to answer was itself a /pc, /gc or /msg command from this
        // account when the local player asked it, so our reply must not follow it immediately.
        if (usesCommand(pending.channel()) && isLocalPlayer(pending.sender())) deferSending();

        List<AiTool> tools = tools();
        if (tools.isEmpty() || modelKey().equals(noToolsModel)) {
            answerWithoutTools(pending);
            return;
        }
        askModel(pending, tools, false);
    }

    /**
     * The old way, for models that cannot call tools: party requests and price questions are
     * recognised by keywords and answered by the mod; everything else goes to the model.
     */
    private void answerWithoutTools(Pending pending) {
        String sender = pending.sender();
        String question = pending.question();

        // Small models keep answering "I can't run commands" however the prompt is written, so the
        // request is recognised here. Instant, and costs no tokens.
        if (allowCommands.enabled()) {
            String direct = commandFor(question);
            if (direct != null) {
                LOGGER.info("[AI] {} asked for a command: /{}", sender, direct);
                runCommand(direct);
                sendReply(pending, "OK: " + direct.substring(2)); // drop the leading "p "
                return;
            }
        }

        // Auction prices come from Coflnet, not from the model: it cannot know today's market and
        // would happily invent a number. Falls through to the model when nothing matches.
        if (priceLookup.enabled() && PriceLookup.isPriceQuestion(question)) {
            CompletableFuture.supplyAsync(() -> PriceLookup.answer(question), AiBackend.WORKER)
                    .thenAccept(line -> Minecraft.getInstance().execute(() -> {
                        if (line != null) sendReply(pending, line);
                        else askModel(pending, List.of(), true);
                    }))
                    .exceptionally(error -> {
                        LOGGER.warn("[AI] price lookup failed", error);
                        Minecraft.getInstance().execute(() -> askModel(pending, List.of(), true));
                        return null;
                    });
            return;
        }

        askModel(pending, List.of(), true);
    }

    /**
     * Sends the conversation to the configured backend and answers with what comes back.
     *
     * @param legacy the model was not given tools and may ask for a command in its text instead
     */
    private void askModel(Pending pending, List<AiTool> tools, boolean legacy) {
        AiBackend ai = buildBackend();
        if (ai == null) {
            sendModMessage(Component.literal("AI is not configured (missing API key).").withStyle(ChatFormatting.RED));
            return;
        }

        List<ChatMessage> conversation = new ArrayList<>(history);
        LOGGER.info("[AI] {} asked in {}: {} ({} remembered, {} tools)",
                pending.sender(), pending.channel().label, pending.question(), conversation.size(), tools.size());

        ai.complete(systemPrompt(tools), conversation, tools)
                .thenAccept(reply -> Minecraft.getInstance()
                        .execute(() -> handleReply(pending, reply, legacy)))
                .exceptionally(error -> {
                    Throwable cause = rootCause(error);
                    if (cause instanceof AiBackend.ToolsUnsupportedException) {
                        // Remembered, so the following questions do not each pay a refused request.
                        LOGGER.info("[AI] {}, answering without tools", cause.getMessage());
                        Minecraft.getInstance().execute(() -> {
                            noToolsModel = modelKey();
                            answerWithoutTools(pending);
                        });
                        return null;
                    }
                    LOGGER.warn("[AI] request failed", error);
                    Minecraft.getInstance().execute(() -> sendModMessage(
                            Component.literal("AI request failed: " + rootMessage(error)).withStyle(ChatFormatting.RED)));
                    return null;
                });
    }

    /** Runs any requested command, then sends the remaining text back where the question came from. */
    private void handleReply(Pending pending, String rawReply, boolean legacy) {
        String reply = rawReply == null ? "" : rawReply.trim();

        // With tools the model runs commands by calling them; text that merely looks like a
        // directive is just text.
        Matcher directive = COMMAND_DIRECTIVE.matcher(reply);
        if (legacy && directive.find()) {
            String action = directive.group(1).toLowerCase(Locale.ROOT);
            boolean needsName = !action.equals("warp");
            if (allowCommands.enabled()) {
                runAllowedCommand(action, needsName ? directive.group(2) : "");
            }
            // "warp" takes no name, so keep the word the pattern swallowed after it — it belongs to
            // the confirmation sentence.
            int cut = needsName ? directive.end() : directive.end(1);
            reply = (reply.substring(0, directive.start()) + " " + reply.substring(cut)).trim();
        }

        reply = reply.replaceAll("\\s+", " ").trim();
        if (reply.isEmpty()) {
            // Don't fail silently — that looks like the mod ignored the question.
            LOGGER.warn("[AI] model returned nothing to say");
            sendModMessage(Component.literal("AI returned an empty answer.").withStyle(ChatFormatting.RED));
            return;
        }
        sendReply(pending, reply);
    }

    /** Tags the answer, remembers it, and queues it for wherever the question came from. */
    private void sendReply(Pending pending, String reply) {
        int room = MAX_REPLY_LENGTH - AI_TAG.length();
        if (reply.length() > room) {
            reply = reply.substring(0, room - 3).trim() + "...";
        }

        remember(ChatMessage.assistant(reply)); // so the AI knows what it already answered

        // The tag goes out with the message, and is what public chat echoes back at us.
        String tagged = AI_TAG + reply;
        lastSentReply = tagged;
        outbox.add(new Outgoing(replyCommand(pending), tagged));
    }

    /**
     * The command that carries a reply, or {@code null} to speak in plain chat.
     *
     * <p>A whisper is the one case where the command depends on the message rather than the
     * channel, because it names the player to answer.
     */
    private static String replyCommand(Pending pending) {
        if (pending.channel() == Channel.WHISPER) {
            return pending.whisperTarget() == null ? null : "msg " + pending.whisperTarget();
        }
        return pending.channel().command;
    }

    /**
     * Releases one queued line per interval.
     *
     * <p>Hypixel silently drops a command sent too soon after another one, so an answer that
     * followed a {@code /p invite} in the same tick never reached the chat. Everything we send now
     * goes through here, spaced out — including after our own commands and after the local player's
     * question, which is itself a {@code /pc} command from this account.
     */
    @Override
    public void onClientTick() {
        // A question held back by the spam cooldown is answered as soon as its turn comes, which
        // may well be while chat is silent — so this has to be driven by the clock, not by chat.
        pump();

        if (sendCooldown > 0) {
            sendCooldown--;
            return;
        }
        if (outbox.isEmpty()) return;

        ClientPacketListener network = Minecraft.getInstance().getConnection();
        if (network == null) return; // not connected yet: hold the line until we are

        Outgoing out = outbox.poll();
        if (out.text() == null) {
            LOGGER.info("[AI] running /{}", out.command());
            network.sendCommand(out.command()); // a party command the AI asked for
        } else if (out.command() == null) {
            network.sendChat(out.text()); // singleplayer / vanilla: plain chat, no /ac
        } else {
            network.sendCommand(out.command() + " " + out.text());
        }
        sendCooldown = TICKS_BETWEEN_SENDS;
    }

    /** Holds the next outgoing line for one interval. */
    private void deferSending() {
        sendCooldown = Math.max(sendCooldown, TICKS_BETWEEN_SENDS);
    }

    // ---- Commands --------------------------------------------------------------------------

    /**
     * The command line for an allowed party action, or {@code null}. Only these commands can ever
     * be run by the AI.
     */
    private static String partyCommand(String action, String argument) {
        String safeArg = argument == null ? "" : argument.trim();
        return switch (action.toLowerCase(Locale.ROOT)) {
            case "invite", "party_invite" -> safeArg.isEmpty() ? null : "p invite " + safeArg;
            case "kick", "party_kick" -> safeArg.isEmpty() ? null : "p kick " + safeArg;
            case "transfer", "party_transfer" -> safeArg.isEmpty() ? null : "p transfer " + safeArg;
            case "promote", "party_promote" -> safeArg.isEmpty() ? null : "p promote " + safeArg;
            case "warp", "party_warp" -> "p warp";
            default -> null;
        };
    }

    private void runAllowedCommand(String action, String argument) {
        String safeArg = argument == null ? "" : argument.trim();
        String command = partyCommand(action, safeArg);
        if (command == null) {
            LOGGER.info("[AI] ignored command directive: {} {}", action, safeArg);
            return;
        }
        runCommand(command);
    }

    private void runCommand(String command) {
        LOGGER.info("[AI] running /{}", command);
        ClientPacketListener network = Minecraft.getInstance().getConnection();
        if (network != null) {
            network.sendCommand(command);
            deferSending(); // the confirmation must not follow this command immediately
        }
    }

    /**
     * Recognises a party request without asking the model, in any of the phrasings players use
     * ("invite Notch", "peux-tu inviter Notch stp", "kick him: Notch").
     *
     * @return the command to run without its slash, or {@code null} when this is a normal question
     */
    private String commandFor(String question) {
        if (QUESTION_ABOUT.matcher(question).find()) return null;

        Action best = null;
        Matcher bestMatch = null;
        for (Action action : Action.values()) {
            Matcher matcher = action.verbs.matcher(question);
            if (!matcher.find()) continue;
            // Several verbs can appear ("invite X and warp"); the first one is the request.
            if (bestMatch == null || matcher.start() < bestMatch.start()) {
                best = action;
                bestMatch = matcher;
            }
        }
        if (best == null) return null;
        if (best == Action.WARP) return "p warp";

        String name = findName(question, bestMatch.end());
        return name == null ? null : best.command + " " + name; // no name: let the model reply
    }

    /**
     * Picks the player name out of the sentence: the first name-shaped word after the verb, or
     * before it if the verb came last ("Notch, invite le stp").
     */
    private static String findName(String question, int fromVerb) {
        String after = findName(question.substring(fromVerb));
        return after != null ? after : findName(question.substring(0, fromVerb));
    }

    private static String findName(String part) {
        Matcher matcher = NAME_TOKEN.matcher(part);
        while (matcher.find()) {
            String word = matcher.group();
            if (!NOT_NAMES.contains(word.toLowerCase(Locale.ROOT))) return word;
        }
        return null;
    }

    /** A party action, its command, and the words players use for it across languages. */
    private enum Action {
        INVITE("p invite", "invit\\w*|inv|add|ajoute\\w*|einlad\\w*|invita\\w*"),
        KICK("p kick", "kick\\w*|expuls\\w*|vire[rz]?|d[ée]gage\\w*|raus\\w*|echa\\w*"),
        TRANSFER("p transfer", "transf[eè]r\\w*|traspas\\w*"),
        PROMOTE("p promote", "promot\\w*|promou\\w*|promeut|promu"),
        WARP("p warp", "warp\\w*");

        final String command;
        final Pattern verbs;

        Action(String command, String verbs) {
            this.command = command;
            this.verbs = Pattern.compile("(?i)\\b(?:" + verbs + ")\\b");
        }
    }

    // ---- Tools -----------------------------------------------------------------------------

    /** What the model may call for this question, as the settings allow. */
    private List<AiTool> tools() {
        List<AiTool> tools = new ArrayList<>();
        if (allowCommands.enabled()) tools.add(partyTool());
        if (priceLookup.enabled()) tools.addAll(SkyblockTools.all());
        return tools;
    }

    /**
     * Runs a party command for the model. The command goes through the outbox like any line we
     * send, so two of them asked in one go are spaced out instead of the second being dropped.
     */
    private AiTool partyTool() {
        JsonObject schema = AiTool.newSchema();
        AiTool.choice(schema, "action", "The party action.", "invite", "kick", "transfer", "promote", "warp");
        AiTool.prop(schema, "player", "string",
                "Exact Minecraft name of the player, copied from the chat. Not needed for warp.");
        AiTool.require(schema, "action");
        return new AiTool("party_command",
                "Runs a Hypixel party command from this account: invite a player, kick them, transfer "
                        + "the party to them, promote them, or warp the party to you. Use it only when the "
                        + "last message asks for it, once per player.",
                schema, args -> {
                    String action = AiTool.string(args, "action").toLowerCase(Locale.ROOT);
                    String player = AiTool.string(args, "player");
                    if (!action.equals("warp") && !PLAYER_NAME.matcher(player).matches()) {
                        return "Error: give the exact Minecraft name (letters, digits or _, up to 16).";
                    }
                    String command = partyCommand(action, action.equals("warp") ? "" : player);
                    if (command == null) return "Error: unknown action " + action;
                    Minecraft.getInstance().execute(() -> outbox.add(new Outgoing(command, null)));
                    return "Done: /" + command;
                });
    }

    /** Identifies the model in use, to remember which one cannot use tools. */
    private String modelKey() {
        return switch (backend.get()) {
            case "Ollama" -> "Ollama:" + ollamaUrl.get().trim() + ":" + ollamaModel.get().trim();
            default -> backend.get() + ":" + apiModel.get().trim();
        };
    }

    /** Adds one turn to the window, dropping the oldest once it is full. */
    private void remember(ChatMessage turn) {
        int limit = memory.get();
        if (limit <= 0) {
            history.clear();
            history.addLast(turn); // no memory: keep only what is being answered right now
            return;
        }
        history.addLast(turn);
        while (history.size() > limit) {
            history.removeFirst();
        }
    }

    /** Forget everything — the conversation does not carry across servers. */
    @Override
    protected void onDisabled() {
        history.clear();
        outbox.clear();
        inbox.clear();
        sendCooldown = 0;
        lastSentReply = "";
    }

    // ---- Prompt controls in the menu --------------------------------------------------------

    private final UiButton editPromptButton = new UiButton("Edit prompt", UiButton.Variant.SECONDARY, () -> {
        PromptStore.openPrompt(prompt.get(), AiChatFeature::defaultPrompt);
        sendModMessage(Component.literal("Prompt \"" + prompt.get() + "\" opened. Save it, the next question uses it.")
                .withStyle(ChatFormatting.GRAY));
    });
    private final UiButton folderButton = new UiButton("Prompt folder", UiButton.Variant.SECONDARY, () -> {
        PromptStore.openPromptFolder(AiChatFeature::defaultPrompt);
        sendModMessage(Component.literal("Each .txt file here is a prompt. Pick it in the Prompt setting.")
                .withStyle(ChatFormatting.GRAY));
    });
    private final UiButton resetPromptButton = new UiButton("Reset", UiButton.Variant.GHOST, () -> {
        resetPrompt();
        sendModMessage(Component.literal("Default prompt restored to the built-in one.").withStyle(ChatFormatting.GRAY));
    });

    @Override
    public void renderExtra(net.minecraft.client.gui.GuiGraphicsExtractor ctx,
                            net.minecraft.client.gui.screens.Screen parent,
                            int left, int y, int right, int mouseX, int mouseY) {
        int x = left;
        for (UiButton button : List.of(editPromptButton, folderButton, resetPromptButton)) {
            button.bounds(x, y, button.preferredWidth(), Theme.BUTTON_H_SMALL).render(ctx, mouseX, mouseY);
            x += button.width() + 4;
        }
    }

    @Override
    public int extraHeight() {
        return Theme.BUTTON_H_SMALL;
    }

    @Override
    public boolean extraMouseClicked(net.minecraft.client.gui.screens.Screen parent,
                                     double mx, double my, int button) {
        return editPromptButton.mouseClicked(mx, my, button) || folderButton.mouseClicked(mx, my, button)
                || resetPromptButton.mouseClicked(mx, my, button);
    }

    // ---- Prompt ----------------------------------------------------------------------------

    /**
     * The prompt actually sent: the prompt file picked in the settings, with its placeholders filled
     * in — {@code {trigger}} becomes the current trigger word, and the {@code [COMMANDS]} block is
     * kept only while "Allow commands" is on — followed by how to use the tools on offer.
     *
     * <p>The tool part is added here rather than written in the file, so every prompt a player
     * writes works with the tools without having to describe them.
     */
    private String systemPrompt(List<AiTool> tools) {
        String text = PromptStore.loadPrompt(prompt.get(), AiChatFeature::defaultPrompt);
        if (allowCommands.enabled()) {
            text = text.replace(COMMANDS_OPEN + "\n", "").replace(COMMANDS_CLOSE + "\n", "")
                       .replace(COMMANDS_OPEN, "").replace(COMMANDS_CLOSE, "");
        } else {
            int start = text.indexOf(COMMANDS_OPEN);
            int end = text.indexOf(COMMANDS_CLOSE);
            if (start >= 0 && end > start) {
                text = text.substring(0, start) + text.substring(end + COMMANDS_CLOSE.length());
            }
        }
        text = text.replace("{trigger}", "!" + trigger.get().trim()).trim();
        return tools.isEmpty() ? text : text + "\n\n" + toolGuide(tools);
    }

    /** How to use the tools, for whichever of them are on offer. */
    private static String toolGuide(List<AiTool> tools) {
        boolean party = AiBackend.find(tools, "party_command") != null;
        boolean market = AiBackend.find(tools, "find_item") != null;
        StringBuilder guide = new StringBuilder("""
                TOOLS
                - You have tools. Use them instead of guessing. They override anything above saying
                  you cannot check prices or run commands.
                - The player may write in any language or slang: work out what they mean, and give
                  the tools English item names.
                """);
        if (market) {
            guide.append("""
                    - Prices and market questions (auction house, bazaar, past sales, price history,
                      recipes): always use the tools, never a number from memory. Usual path: find_item,
                      then quick_price; for specific attributes get_item_filters then search_auctions;
                      get_bazaar_price for bazaar items; coflnet_api for anything else.
                    - Quote prices short, like 494.5M. Name the seller only if asked. You may end with
                      "/viewauction <auction id>" so players can open the listing.
                    - If a tool finds nothing, say so in a few words. Do not invent.
                    """);
        }
        if (party) {
            guide.append("""
                    - Party actions (invite, kick, transfer, promote, warp): call party_command only
                      when the last message asks for one, then confirm in a few words, e.g. "Invited Notch".
                    """);
        }
        guide.append("- Your final answer is still one short line.");
        return guide.toString();
    }

    /** Restores the shipped Default prompt, overwriting the player's edits of it. */
    public void resetPrompt() {
        PromptStore.resetDefaultPrompt(AiChatFeature::defaultPrompt);
    }

    /**
     * The shipped prompt: an output contract, the language rule, how to read a busy chat, and a few
     * worked examples.
     *
     * <p>It says nothing about tools: {@link #toolGuide} is appended to whichever prompt is picked,
     * so a player's own prompts get the same instructions without having to write them.
     */
    private static String defaultPrompt() {
        return """
                # Woad - AI Chat prompt.
                # Every .txt file in this folder is a prompt: copy this one, rename it, edit it,
                # then pick it in the menu (AI Chat > Prompt).
                # Lines starting with # are comments and are NOT sent to the AI.
                # {trigger} is replaced by the trigger word from the settings (e.g. !ai).
                # How to use the tools (prices, party commands) is added by the mod: no need to write it.
                # Delete this file, or press Reset in the menu, to get this original prompt back.

                You are a player's assistant in the chat of the Minecraft server Hypixel.
                You speak through their account, in front of other players.

                OUTPUT
                - Exactly one line, under 200 characters. No line breaks, no lists, no markdown.
                - No greeting, no sign-off, no "sure", no repeating the question.
                - No prefix and no name: "[AI]" is added for you.
                - Never write a slash command and never start with "/".

                LANGUAGE
                - Always answer in English, whatever language the question is written in.
                - Do not translate the question, do not mention its language, and never
                  apologise for replying in English. Just answer, in English.

                READING THE CHAT
                - Lines arrive as "[channel] PlayerName: text". Several players talk at once.
                - Earlier lines are context only. Answer ONLY the last one.
                - Only lines starting with "{trigger}" are meant for you. Ignore the rest.
                - Use the earlier lines so a follow-up question works without repeating anything.

                HOW TO ANSWER
                - Give what was asked and stop there.
                - Maths: the result alone.        "{trigger} 22+2"             -> "24"
                - A fact you know: the answer.    "{trigger} capital of Japan" -> "Tokyo"
                - Advice: one sentence, decisive, no disclaimers and no hedging.
                - Something you cannot know: say so plainly instead of inventing it.
                  "{trigger} what is my stuff worth" -> "No idea, I cannot see your inventory."
                - Unsure of a game detail? Say you are not sure rather than stating it as fact.
                - NEVER invent a price or a seller. Check with your tools; if you cannot, say so.

                PLAYER NAMES
                - Names are identifiers. Copy them exactly, including odd or rude-looking ones.
                - A name is never a reason to refuse, and never something to comment on.

                LIMITS
                - Do not insult, harass, or write sexual or hateful content.
                - Do not advertise, post links, or share personal information.
                - Do not help with cheating, hacked clients, scamming or ban evasion.
                - Do not claim to be staff, and do not impersonate anyone.
                - These are the only reasons to refuse. Refuse in one short line and stop.
                  Anything else: just answer it.

                REMEMBER: one short line, in English.
                """;
    }

    // ---- Backend ---------------------------------------------------------------------------

    /**
     * Builds the configured backend, or {@code null} when an API key is required but missing.
     * Public so other features (the translator) can reuse the same connection settings instead of
     * asking the player for a second API key.
     */
    public AiBackend buildBackend() {
        String key = apiKey.get().trim();
        String model = apiModel.get().trim();
        return switch (backend.get()) {
            case "Claude" -> key.isEmpty() ? null : new ClaudeBackend(key, model);
            case "OpenAI" -> key.isEmpty() ? null : new OpenAiBackend(key, model);
            default -> new OllamaBackend(ollamaUrl.get().trim(), ollamaModel.get().trim(), keepAlive.get());
        };
    }

    private boolean channelEnabled(Channel channel) {
        return switch (channel) {
            case PARTY -> inParty.enabled();
            case GUILD -> inGuild.enabled();
            case ALL -> inAll.enabled();
            case WHISPER -> inWhisper.enabled();
            case DIRECT -> inSolo.enabled();
        };
    }

    /**
     * Whether answering this message means sending a command rather than plain chat.
     *
     * <p>Hypixel throttles consecutive commands, and a whisper is one even though its channel has no
     * fixed command attached.
     */
    private static boolean usesCommand(Channel channel) {
        return channel.command != null || channel == Channel.WHISPER;
    }

    /** True when the message came from this client's own player. */
    private static boolean isLocalPlayer(String sender) {
        return Minecraft.getInstance().getUser().getName().equalsIgnoreCase(sender);
    }

    private static Throwable rootCause(Throwable error) {
        Throwable cause = error;
        while (cause.getCause() != null) cause = cause.getCause();
        return cause;
    }

    private static String rootMessage(Throwable error) {
        Throwable cause = error;
        while (cause.getCause() != null) cause = cause.getCause();
        String message = cause.getMessage();
        return message == null ? cause.getClass().getSimpleName() : message;
    }

    /** A chat channel: how we recognise it and how we answer in it ({@code null} = plain chat). */
    private enum Channel {
        PARTY("party", "pc"),
        GUILD("guild", "gc"),
        ALL("public", "ac"),
        /** A whisper: the command is built per message, since it carries the recipient's name. */
        WHISPER("dm", null),
        DIRECT("public", null);

        final String label;
        final String command;

        Channel(String label, String command) {
            this.label = label;
            this.command = command;
        }
    }
}
