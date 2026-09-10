package com.pureblue.woad.features;

import com.pureblue.woad.ai.AiBackend;
import com.pureblue.woad.ai.ChatMessage;
import com.pureblue.woad.ai.ClaudeBackend;
import com.pureblue.woad.ai.OllamaBackend;
import com.pureblue.woad.ai.OpenAiBackend;
import com.pureblue.woad.ai.PriceLookup;
import com.pureblue.woad.ai.PromptStore;
import com.pureblue.woad.core.Feature;
import com.pureblue.woad.core.setting.BooleanSetting;
import com.pureblue.woad.core.setting.IntSetting;
import com.pureblue.woad.core.setting.ModeSetting;
import com.pureblue.woad.core.setting.StringSetting;
import com.pureblue.woad.gui.SmallButton;
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
 * and to keep answers short. It can run a small allow-list of party commands
 * (invite, kick, warp, ...) when a player asks for one — never arbitrary commands.
 *
 * <p>Backends: a local Ollama model, or an API key (Claude / OpenAI).
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
     * The model asks for an action with: {@code [CMD] <action> <player>}. Only the allowed actions
     * are part of the pattern, so a stray "CMD" in a sentence cannot trigger anything, and the
     * brackets / punctuation around the marker are optional — models format it loosely.
     */
    private static final Pattern COMMAND_DIRECTIVE = Pattern.compile(
            "(?i)\\[?CMD]?\\s*[:\\-]?\\s*(?:party[_ ])?(invite|kick|transfer|promote|warp)\\b[\\s:]*(\\w{0,16})");

    /** Minecraft names are 3-16 word characters. */
    private static final Pattern NAME_TOKEN = Pattern.compile("[A-Za-z0-9_]{3,16}");

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

    private final BooleanSetting inSolo = addSetting(new BooleanSetting("Solo / other servers",
            "Answer in normal chat outside Hypixel (singleplayer worlds, vanilla servers). "
                    + "Handy for testing.", true));

    private final BooleanSetting allowCommands = addSetting(new BooleanSetting("Allow commands",
            "Let the AI run party commands (invite, kick, warp) when a player asks.", true));

    private final BooleanSetting priceLookup = addSetting(new BooleanSetting("Auction prices",
            "Answer price questions from Coflnet's live auction data instead of asking the AI. "
                    + "Understands filters too: \"cheapest hyperion with wither impact\".", true));

    private final IntSetting memory = addSetting(new IntSetting("Memory",
            "How many recent chat messages the AI remembers, so a conversation can span several "
                    + "messages. Higher means better memory but slower answers. 0 disables it.",
            100, 0, 500));

    private long lastReplyAt = 0L;
    /** Our own last answer — public chat echoes it back to us, and answering it would loop. */
    private String lastSentReply = "";

    /**
     * Rolling window of what was recently said, oldest first: every player message from an enabled
     * channel plus our own answers. Sent with each request so the AI can hold a conversation
     * instead of seeing each question in isolation.
     */
    private final Deque<ChatMessage> history = new ArrayDeque<>();

    /** A line waiting to be sent, and the channel it belongs to. */
    private record Outgoing(Channel channel, String text) {}

    /** Lines waiting their turn, so two never leave the client in the same tick. */
    private final Deque<Outgoing> outbox = new ArrayDeque<>();
    private int sendCooldown = 0;

    public AiChatFeature() {
        super("ai_chat", "AI Chat",
                "Answers in chat when a player writes your trigger command. Works with a local "
                        + "Ollama model or an API key.",
                false);
    }

    // ---- Chat handling ---------------------------------------------------------------------

    @Override
    public void onChatMessage(String message) {
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

        tryAnswer(matcher.group(1), matcher.group(2).trim(), channel);
    }

    /**
     * Player chat outside Hypixel (singleplayer, vanilla servers). The sender and text arrive
     * already separated, so there is nothing to parse — and the answer goes back as a normal chat
     * message, since {@code /ac} does not exist there.
     */
    @Override
    public void onPlayerChatMessage(String sender, String content) {
        if (!inSolo.enabled() || content == null) return;
        tryAnswer(sender, content.trim(), Channel.DIRECT);
    }

    /** Shared path: check the trigger, then ask the model and answer in {@code channel}. */
    private void tryAnswer(String sender, String body, Channel channel) {
        if (!lastSentReply.isEmpty() && body.equals(lastSentReply)) return; // never answer ourselves

        // Remember everything players say here, not just questions — that is the conversation
        // context the AI needs to follow a discussion.
        remember(ChatMessage.user("[" + channel.label + "] " + sender + ": " + body));

        String prefix = "!" + trigger.get().trim().toLowerCase(Locale.ROOT);
        if (!body.toLowerCase(Locale.ROOT).startsWith(prefix)) return;

        String question = body.substring(prefix.length()).trim();
        if (question.isEmpty()) return;

        long now = System.currentTimeMillis();
        if (now - lastReplyAt < COOLDOWN_MS) return;
        lastReplyAt = now;

        // The question we are about to answer was itself a /pc or /gc command from this account
        // when the local player asked it, so our reply must not follow it immediately.
        if (channel.command != null && isLocalPlayer(sender)) deferSending();

        // Party management is handled here, not by the model: small models keep answering "I can't
        // run commands" however the prompt is written. This is also instant and costs no tokens.
        if (allowCommands.enabled()) {
            String direct = commandFor(question);
            if (direct != null) {
                LOGGER.info("[AI] {} asked for a command: /{}", sender, direct);
                runCommand(direct);
                sendReply(channel, "OK: " + direct.substring(2)); // drop the leading "p "
                return;
            }
        }

        // Auction prices come from Coflnet, not from the model: it cannot know today's market and
        // would happily invent a number. Falls through to the model when nothing matches.
        if (priceLookup.enabled() && PriceLookup.isPriceQuestion(question)) {
            final Channel priceChannel = channel;
            final String asked = question;
            CompletableFuture.supplyAsync(() -> PriceLookup.answer(asked))
                    .thenAccept(line -> Minecraft.getInstance().execute(() -> {
                        if (line != null) sendReply(priceChannel, line);
                        else askModel(priceChannel, asked, sender);
                    }))
                    .exceptionally(error -> {
                        LOGGER.warn("[AI] price lookup failed", error);
                        Minecraft.getInstance().execute(() -> askModel(priceChannel, asked, sender));
                        return null;
                    });
            return;
        }

        askModel(channel, question, sender);
    }

    /** Sends the conversation to the configured backend and answers with what comes back. */
    private void askModel(Channel channel, String question, String sender) {
        AiBackend ai = buildBackend();
        if (ai == null) {
            sendModMessage(Component.literal("AI is not configured (missing API key).").withStyle(ChatFormatting.RED));
            return;
        }

        List<ChatMessage> conversation = new ArrayList<>(history);
        LOGGER.info("[AI] {} asked in {}: {} ({} remembered)", sender, channel.label, question, conversation.size());

        final Channel replyChannel = channel;
        ai.complete(systemPrompt(), conversation)
                .thenAccept(reply -> Minecraft.getInstance()
                        .execute(() -> handleReply(replyChannel, reply)))
                .exceptionally(error -> {
                    LOGGER.warn("[AI] request failed", error);
                    Minecraft.getInstance().execute(() -> sendModMessage(
                            Component.literal("AI request failed: " + rootMessage(error)).withStyle(ChatFormatting.RED)));
                    return null;
                });
    }

    /** Runs any requested command, then sends the remaining text back to the same channel. */
    private void handleReply(Channel channel, String rawReply) {
        String reply = rawReply == null ? "" : rawReply.trim();

        Matcher directive = COMMAND_DIRECTIVE.matcher(reply);
        if (directive.find()) {
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
        sendReply(channel, reply);
    }

    /** Tags the answer, remembers it, and queues it for the channel the question came from. */
    private void sendReply(Channel channel, String reply) {
        int room = MAX_REPLY_LENGTH - AI_TAG.length();
        if (reply.length() > room) {
            reply = reply.substring(0, room - 3).trim() + "...";
        }

        remember(ChatMessage.assistant(reply)); // so the AI knows what it already answered

        // The tag goes out with the message, and is what public chat echoes back at us.
        String tagged = AI_TAG + reply;
        lastSentReply = tagged;
        outbox.add(new Outgoing(channel, tagged));
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
        if (sendCooldown > 0) {
            sendCooldown--;
            return;
        }
        if (outbox.isEmpty()) return;

        ClientPacketListener network = Minecraft.getInstance().getConnection();
        if (network == null) return; // not connected yet: hold the line until we are

        Outgoing out = outbox.poll();
        if (out.channel().command == null) {
            network.sendChat(out.text()); // singleplayer / vanilla: plain chat, no /ac
        } else {
            network.sendCommand(out.channel().command + " " + out.text());
        }
        sendCooldown = TICKS_BETWEEN_SENDS;
    }

    /** Holds the next outgoing line for one interval. */
    private void deferSending() {
        sendCooldown = Math.max(sendCooldown, TICKS_BETWEEN_SENDS);
    }

    // ---- Commands --------------------------------------------------------------------------

    /** Only these commands can ever be run by the AI. */
    private void runAllowedCommand(String action, String argument) {
        String safeArg = argument == null ? "" : argument.trim();
        String command = switch (action.toLowerCase(Locale.ROOT)) {
            case "invite", "party_invite" -> safeArg.isEmpty() ? null : "p invite " + safeArg;
            case "kick", "party_kick" -> safeArg.isEmpty() ? null : "p kick " + safeArg;
            case "transfer", "party_transfer" -> safeArg.isEmpty() ? null : "p transfer " + safeArg;
            case "promote", "party_promote" -> safeArg.isEmpty() ? null : "p promote " + safeArg;
            case "warp", "party_warp" -> "p warp";
            default -> null;
        };
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
        sendCooldown = 0;
        lastSentReply = "";
    }

    // ---- Prompt controls in the menu --------------------------------------------------------

    private final int[] editRect = new int[4];
    private final int[] resetRect = new int[4];

    @Override
    public void renderExtra(net.minecraft.client.gui.GuiGraphicsExtractor ctx,
                            net.minecraft.client.gui.screens.Screen parent,
                            int left, int y, int right, int mouseX, int mouseY) {
        int gap = 4;
        int resetWidth = 52;
        int editWidth = Math.max(60, right - left - resetWidth - gap);
        SmallButton.draw(ctx, editRect, left, y, editWidth, "Edit prompt", mouseX, mouseY);
        SmallButton.draw(ctx, resetRect, left + editWidth + gap, y, resetWidth, "Reset", mouseX, mouseY);
    }

    @Override
    public int extraHeight() {
        return SmallButton.HEIGHT + 4;
    }

    @Override
    public boolean extraMouseClicked(net.minecraft.client.gui.screens.Screen parent,
                                     double mx, double my, int button) {
        if (button != 0) return false;
        if (SmallButton.hit(editRect, mx, my)) {
            openPrompt();
            sendModMessage(Component.literal("Prompt file opened. Save it, the next question uses it.")
                    .withStyle(ChatFormatting.GRAY));
            return true;
        }
        if (SmallButton.hit(resetRect, mx, my)) {
            resetPrompt();
            sendModMessage(Component.literal("Prompt reset to the built-in one.").withStyle(ChatFormatting.GRAY));
            return true;
        }
        return false;
    }

    // ---- Prompt ----------------------------------------------------------------------------

    /**
     * The prompt actually sent: the (editable) file from the config folder, with its placeholders
     * filled in — {@code {trigger}} becomes the current trigger word, and the {@code [COMMANDS]}
     * block is kept only while "Allow commands" is on.
     */
    private String systemPrompt() {
        String text = PromptStore.load(PromptStore.AI_CHAT, AiChatFeature::defaultPrompt);
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
        return text.replace("{trigger}", "!" + trigger.get().trim()).trim();
    }

    /** Restores the shipped prompt, overwriting whatever is in the config folder. */
    public void resetPrompt() {
        PromptStore.write(PromptStore.AI_CHAT, defaultPrompt());
    }

    /** Opens the prompt file in the system's text editor. */
    public void openPrompt() {
        PromptStore.open(PromptStore.AI_CHAT, AiChatFeature::defaultPrompt);
    }

    /**
     * The shipped prompt: an output contract, the language rule, how to read a busy chat, and a few
     * worked examples.
     *
     * <p>It deliberately says nothing about party commands. Those are recognised and run by
     * {@link #commandFor} before the model is ever called, so telling it about them would only
     * invite it to argue about whether it can — which is exactly what small models do.
     */
    private static String defaultPrompt() {
        return """
                # Woad - AI Chat prompt.
                # Lines starting with # are comments and are NOT sent to the AI.
                # {trigger} is replaced by the trigger word from the settings (e.g. !ai).
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
                - NEVER state an auction price or name a seller. You cannot see the auction house;
                  the mod answers those itself. Say "I cannot check the auction house" instead.

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
            case DIRECT -> inSolo.enabled();
        };
    }

    /** True when the message came from this client's own player. */
    private static boolean isLocalPlayer(String sender) {
        return Minecraft.getInstance().getUser().getName().equalsIgnoreCase(sender);
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
        DIRECT("public", null);

        final String label;
        final String command;

        Channel(String label, String command) {
            this.label = label;
            this.command = command;
        }
    }
}
