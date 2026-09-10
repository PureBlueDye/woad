package com.pureblue.woad.ai;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * A chat-completion backend. Implementations take a system prompt and the conversation so far, and
 * return the model's reply asynchronously (never on the client thread).
 */
public interface AiBackend {

    /**
     * Requests a completion.
     *
     * @param systemPrompt the rules/persona prompt
     * @param messages     the conversation, oldest first; the last entry is what to answer
     * @return the model's raw reply text
     */
    CompletableFuture<String> complete(String systemPrompt, List<ChatMessage> messages);
}
