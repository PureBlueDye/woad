package com.pureblue.woad.ai;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * A chat-completion backend. Implementations take a system prompt and the conversation so far, and
 * return the model's reply asynchronously (never on the client thread).
 */
public interface AiBackend {

    /**
     * How many times the model may call tools before it has to answer. A price question takes two
     * or three rounds (find the item, read its filters, search); more than this is a model going in
     * circles.
     */
    int MAX_TOOL_ROUNDS = 6;

    /**
     * Where an exchange runs. It blocks on the network for seconds at a time (the model, then every
     * Coflnet call a tool makes), which has no business on the shared common pool.
     */
    java.util.concurrent.Executor WORKER = java.util.concurrent.Executors.newCachedThreadPool(task -> {
        Thread thread = new Thread(task, "Woad AI");
        thread.setDaemon(true);
        return thread;
    });

    /**
     * Requests a completion, letting the model call {@code tools} along the way.
     *
     * <p>The backend runs the whole exchange: each time the model asks for a tool it is run and its
     * result sent back, until the model writes its answer.
     *
     * @param systemPrompt the rules/persona prompt
     * @param messages     the conversation, oldest first; the last entry is what to answer
     * @param tools        what the model may call; empty for a plain answer
     * @return the model's final reply text
     * @throws ToolsUnsupportedException (through the future) when the model cannot use tools at all
     */
    CompletableFuture<String> complete(String systemPrompt, List<ChatMessage> messages, List<AiTool> tools);

    /** A plain completion, without tools. */
    default CompletableFuture<String> complete(String systemPrompt, List<ChatMessage> messages) {
        return complete(systemPrompt, messages, List.of());
    }

    /** Finds a tool by the name the model used. */
    static AiTool find(List<AiTool> tools, String name) {
        for (AiTool tool : tools) {
            if (tool.name().equals(name)) return tool;
        }
        return null;
    }

    /** Runs a tool the model asked for, or explains that it does not exist. */
    static String run(List<AiTool> tools, String name, com.google.gson.JsonObject arguments) {
        AiTool tool = find(tools, name);
        String result = tool == null ? "Error: there is no tool called " + name : tool.call(arguments);
        org.slf4j.LoggerFactory.getLogger("Woad").info("[AI] tool {} {} -> {}", name, arguments,
                result.length() > 300 ? result.substring(0, 300) + "…" : result);
        return result;
    }

    /**
     * The model (a small local one, typically) cannot call tools. The caller falls back to answering
     * without them.
     */
    class ToolsUnsupportedException extends RuntimeException {
        public ToolsUnsupportedException(String message) {
            super(message);
        }
    }
}
