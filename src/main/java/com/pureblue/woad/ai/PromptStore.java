package com.pureblue.woad.ai;

import com.pureblue.woad.core.Woad;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * Keeps the AI prompts in editable text files under {@code config/woad/}.
 *
 * <p>Each file is created from the built-in default the first time it is needed, and re-read on
 * every request — so an edit takes effect on the next question, with no restart. Deleting a file
 * restores the default.
 *
 * <p>Lines starting with {@code #} are comments and are stripped before the prompt is sent, which
 * lets the generated file document its own placeholders.
 */
public final class PromptStore {

    private static final Logger LOGGER = LoggerFactory.getLogger("Woad");

    private static final Path DIR =
            FabricLoader.getInstance().getConfigDir().resolve(Woad.MOD_ID);

    public static final String TRANSLATOR = "translator_prompt.txt";

    private PromptStore() {}

    /**
     * Reads a prompt file, creating it from {@code fallback} when missing or empty.
     *
     * @param fileName a prompt file directly under {@code config/woad/}, e.g. {@link #TRANSLATOR}
     * @param fallback the built-in default, only evaluated when the file has to be written
     */
    public static String load(String fileName, Supplier<String> fallback) {
        Path file = DIR.resolve(fileName);
        try {
            if (Files.exists(file)) {
                String text = strip(Files.readString(file, StandardCharsets.UTF_8));
                if (!text.isBlank()) return text;
            }
        } catch (IOException e) {
            LOGGER.warn("[AI] could not read {}, using the built-in prompt", fileName, e);
            return fallback.get();
        }
        String def = fallback.get();
        write(fileName, def);
        return def;
    }

    /** Writes (or restores) a prompt file. */
    public static void write(String fileName, String contents) {
        try {
            Files.createDirectories(DIR);
            Files.writeString(DIR.resolve(fileName), contents, StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOGGER.warn("[AI] could not write {}", fileName, e);
        }
    }

    /**
     * Opens the file in the system's text editor, creating it from {@code fallback} first if it is
     * not there yet.
     */
    public static void open(String fileName, Supplier<String> fallback) {
        Path file = DIR.resolve(fileName);
        if (!Files.exists(file)) {
            write(fileName, fallback.get());
        }
        Util.getPlatform().openPath(file);
    }

    // ---- AI Chat prompt library ---------------------------------------------------------------

    /**
     * The AI Chat prompts: every {@code .txt} file in this folder is one prompt, named after the
     * file, and the menu picks which one is used. Players add a prompt by dropping a file in.
     */
    public static final Path PROMPTS_DIR = DIR.resolve("ai_prompts");

    /** The prompt that always exists: the built-in one, or the player's edit of it. */
    public static final String DEFAULT_PROMPT = "Default";

    /**
     * Before the library, the single prompt lived here. Its content is carried over as the Default
     * prompt, so nobody loses an edit.
     */
    private static final String LEGACY_AI_CHAT = "ai_chat_prompt.txt";

    /**
     * The line the old built-in prompt used about prices. The AI can now check them, so a migrated
     * prompt that still carries it word for word gets the new wording instead.
     */
    private static final String LEGACY_PRICE_RULE = """
            - NEVER state an auction price or name a seller. You cannot see the auction house;
              the mod answers those itself. Say "I cannot check the auction house" instead.""";
    private static final String PRICE_RULE = """
            - NEVER invent a price or a seller. Check with your tools; if you cannot, say so.""";

    /** The old file's comment header, replaced by one that explains the prompt folder. */
    private static final String LEGACY_HEADER = """
            # Woad - AI Chat prompt.
            # Lines starting with # are comments and are NOT sent to the AI.
            # {trigger} is replaced by the trigger word from the settings (e.g. !ai).
            # Delete this file, or press Reset in the menu, to get this original prompt back.""";
    private static final String HEADER = """
            # Woad - AI Chat prompt.
            # Every .txt file in this folder is a prompt: copy this one, rename it, edit it,
            # then pick it in the menu (AI Chat > Prompt).
            # Lines starting with # are comments and are NOT sent to the AI.
            # {trigger} is replaced by the trigger word from the settings (e.g. !ai).
            # How to use the tools (prices, party commands) is added by the mod: no need to write it.
            # Delete this file, or press Reset in the menu, to get this original prompt back.""";

    /**
     * The names of the available prompts, {@link #DEFAULT_PROMPT} first, then the rest
     * alphabetically. Creates the folder and the Default prompt when they are missing.
     */
    public static List<String> promptNames(Supplier<String> builtIn) {
        ensureLibrary(builtIn);
        List<String> names = new ArrayList<>();
        try (Stream<Path> files = Files.list(PROMPTS_DIR)) {
            files.filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString())
                    .filter(name -> name.toLowerCase(Locale.ROOT).endsWith(".txt"))
                    .map(name -> name.substring(0, name.length() - 4))
                    .filter(name -> !name.equalsIgnoreCase(DEFAULT_PROMPT))
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .forEach(names::add);
        } catch (IOException e) {
            LOGGER.warn("[AI] could not list {}", PROMPTS_DIR, e);
        }
        names.add(0, DEFAULT_PROMPT);
        return names;
    }

    /**
     * Reads one prompt of the library. A prompt that disappeared (file renamed or deleted) or is
     * empty falls back to the Default one rather than leaving the AI without instructions.
     */
    public static String loadPrompt(String name, Supplier<String> builtIn) {
        ensureLibrary(builtIn);
        Path file = promptFile(name);
        try {
            if (Files.exists(file)) {
                String text = strip(Files.readString(file, StandardCharsets.UTF_8));
                if (!text.isBlank()) return text;
            }
            Path fallback = promptFile(DEFAULT_PROMPT);
            if (Files.exists(fallback)) {
                String text = strip(Files.readString(fallback, StandardCharsets.UTF_8));
                if (!text.isBlank()) return text;
            }
        } catch (IOException e) {
            LOGGER.warn("[AI] could not read prompt {}, using the built-in one", name, e);
        }
        return strip(builtIn.get());
    }

    /** Opens one prompt in the system's text editor. */
    public static void openPrompt(String name, Supplier<String> builtIn) {
        ensureLibrary(builtIn);
        Path file = promptFile(name);
        Util.getPlatform().openPath(Files.exists(file) ? file : promptFile(DEFAULT_PROMPT));
    }

    /** Opens the prompt folder, where new prompts are added. */
    public static void openPromptFolder(Supplier<String> builtIn) {
        ensureLibrary(builtIn);
        Util.getPlatform().openPath(PROMPTS_DIR);
    }

    /** Puts the built-in prompt back in the Default file. The other prompts are left alone. */
    public static void resetDefaultPrompt(Supplier<String> builtIn) {
        writeTo(promptFile(DEFAULT_PROMPT), builtIn.get());
    }

    /**
     * Creates the folder and its Default prompt — from the old single prompt file when there is
     * one, so an edited prompt survives the move.
     */
    private static void ensureLibrary(Supplier<String> builtIn) {
        Path defaultFile = promptFile(DEFAULT_PROMPT);
        if (Files.exists(defaultFile)) return;
        String contents = builtIn.get();
        Path legacy = DIR.resolve(LEGACY_AI_CHAT);
        try {
            if (Files.exists(legacy)) {
                String old = Files.readString(legacy, StandardCharsets.UTF_8).replace("\r\n", "\n");
                if (!strip(old).isBlank()) {
                    contents = old.replace(LEGACY_PRICE_RULE, PRICE_RULE).replace(LEGACY_HEADER, HEADER);
                }
            }
        } catch (IOException e) {
            LOGGER.warn("[AI] could not read {}, starting from the built-in prompt", LEGACY_AI_CHAT, e);
        }
        writeTo(defaultFile, contents);
        try {
            Files.deleteIfExists(legacy);
        } catch (IOException e) {
            LOGGER.warn("[AI] could not remove {}", LEGACY_AI_CHAT, e);
        }
    }

    private static Path promptFile(String name) {
        // A name comes from the folder listing or the config file; never let it leave the folder.
        String safe = name == null ? DEFAULT_PROMPT : name.replaceAll("[\\\\/:*?\"<>|]", "").trim();
        if (safe.isEmpty() || safe.startsWith(".")) safe = DEFAULT_PROMPT;
        return PROMPTS_DIR.resolve(safe + ".txt");
    }

    private static void writeTo(Path file, String contents) {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, contents, StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOGGER.warn("[AI] could not write {}", file, e);
        }
    }

    /** Removes comment lines so the file can explain itself without confusing the model. */
    private static String strip(String raw) {
        StringBuilder out = new StringBuilder();
        for (String line : raw.split("\r?\n", -1)) {
            if (line.startsWith("#")) continue;
            out.append(line).append('\n');
        }
        return out.toString().trim();
    }
}
