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
import java.util.function.Supplier;

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

    public static final String AI_CHAT = "ai_chat_prompt.txt";
    public static final String TRANSLATOR = "translator_prompt.txt";

    private PromptStore() {}

    /**
     * Reads a prompt file, creating it from {@code fallback} when missing or empty.
     *
     * @param fileName one of {@link #AI_CHAT} / {@link #TRANSLATOR}
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
