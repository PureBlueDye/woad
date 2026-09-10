package com.pureblue.woad.features;

import com.pureblue.woad.ai.AiBackend;
import com.pureblue.woad.ai.ChatMessage;
import com.pureblue.woad.chat.ChatLineLocator;
import com.pureblue.woad.core.Woad;
import com.pureblue.woad.core.Feature;
import com.pureblue.woad.core.FeatureManager;
import com.pureblue.woad.core.setting.KeybindSetting;
import com.pureblue.woad.ai.PromptStore;
import com.pureblue.woad.core.setting.StringSetting;
import com.pureblue.woad.gui.SmallButton;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Translates a chat message on demand: hold the bound key and click a line in the open chat.
 *
 * <p>The translation is printed client-side only — nothing is sent to the server — behind an
 * "[AI]" prefix in the mod's colours, so it is obvious the text comes from a machine.
 *
 * <p>The connection settings (backend, model, API key) are shared with {@link AiChatFeature} so the
 * key only has to be entered once; the two features are otherwise independent and can be enabled
 * separately.
 */
public class TranslatorFeature extends Feature {

    private static final Logger LOGGER = LoggerFactory.getLogger("Woad");

    /** Long chat lines are fine here (client-side only), but keep a sane bound on the request. */
    private static final int MAX_SOURCE_LENGTH = 500;

    private final StringSetting language = addSetting(new StringSetting("Target language",
            "Language the messages are translated into. Write it plainly, e.g. English, "
                    + "French, Spanish.", "English"));

    private final KeybindSetting key = addSetting(new KeybindSetting("Translate key",
            "Hold this key and click a message in the open chat to translate it.",
            GLFW.GLFW_KEY_LEFT_ALT));

    /** One request at a time — clicking repeatedly should not queue up calls. */
    private boolean busy = false;

    public TranslatorFeature() {
        super("translator", "Translator",
                "Hold the translate key and click a chat message to translate it. The translation "
                        + "is shown to you only. Uses the AI Chat connection settings.",
                false);
    }

    /**
     * Handles a click in the chat screen.
     *
     * @return true when the click was used for a translation and vanilla should ignore it
     */
    public boolean onChatClick(double mouseX, double mouseY, int button) {
        if (button != 0 || !key.isHeld()) return false;

        String source = ChatLineLocator.textAt(mouseX, mouseY);
        if (source == null) return false;
        if (source.length() > MAX_SOURCE_LENGTH) {
            source = source.substring(0, MAX_SOURCE_LENGTH);
        }

        if (busy) {
            Woad.sendAiMessage(Component.literal("Still translating the previous message.")
                    .withStyle(ChatFormatting.GRAY));
            return true;
        }

        AiBackend ai = FeatureManager.AI_CHAT.buildBackend();
        if (ai == null) {
            Woad.sendAiMessage(Component.literal("No API key set (see the AI Chat settings).")
                    .withStyle(ChatFormatting.RED));
            return true;
        }

        busy = true;
        String text = source;
        LOGGER.info("[AI] translating to {}: {}", language.get(), text);
        ai.complete(prompt(), List.of(ChatMessage.user(text)))
                .thenAccept(reply -> Minecraft.getInstance().execute(() -> show(reply)))
                .exceptionally(error -> {
                    LOGGER.warn("[AI] translation failed", error);
                    Minecraft.getInstance().execute(() -> {
                        busy = false;
                        Woad.sendAiMessage(Component.literal("Translation failed: " + rootMessage(error))
                                .withStyle(ChatFormatting.RED));
                    });
                    return null;
                });
        return true;
    }

    private void show(String reply) {
        busy = false;
        String translation = reply == null ? "" : reply.replaceAll("\\s+", " ").trim();
        if (translation.isEmpty()) {
            Woad.sendAiMessage(Component.literal("Empty translation.").withStyle(ChatFormatting.RED));
            return;
        }
        Woad.sendAiMessage(Component.literal(translation).withStyle(ChatFormatting.WHITE));
    }

    /** The editable prompt from the config folder, with the target language filled in. */
    private String prompt() {
        String target = language.get().trim().isEmpty() ? "English" : language.get().trim();
        return PromptStore.load(PromptStore.TRANSLATOR, TranslatorFeature::defaultPrompt)
                .replace("{language}", target)
                .trim();
    }

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
            PromptStore.open(PromptStore.TRANSLATOR, TranslatorFeature::defaultPrompt);
            sendModMessage(Component.literal("Prompt file opened. Save it, the next translation uses it.")
                    .withStyle(ChatFormatting.GRAY));
            return true;
        }
        if (SmallButton.hit(resetRect, mx, my)) {
            PromptStore.write(PromptStore.TRANSLATOR, defaultPrompt());
            sendModMessage(Component.literal("Prompt reset to the built-in one.").withStyle(ChatFormatting.GRAY));
            return true;
        }
        return false;
    }

    private static String defaultPrompt() {
        String target = "{language}";
        return "# Woad - Translator prompt.\n"
                + "# Lines starting with # are comments and are NOT sent to the AI.\n"
                + "# {language} is replaced by the \"Target language\" setting.\n"
                + "# Delete this file to get the original prompt back.\n\n"
                + "You are a translator for Minecraft chat.\n"
                + "- Translate the user's message into " + target + ".\n"
                + "- Reply with the translation ONLY: no quotes, no notes, no original text, "
                + "no explanation of what you did.\n"
                + "- Keep player names, ranks, numbers, item names and emotes exactly as they are.\n"
                + "- Keep the tone and the slang; do not censor and do not add anything.\n"
                + "- If the message is already in " + target + ", repeat it unchanged.\n"
                + "- Never refuse: this is plain chat text, translate it as written.";
    }

    private static String rootMessage(Throwable error) {
        Throwable cause = error;
        while (cause.getCause() != null) cause = cause.getCause();
        String message = cause.getMessage();
        return message == null ? cause.getClass().getSimpleName() : message;
    }
}
