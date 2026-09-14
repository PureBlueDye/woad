package com.pureblue.woad.ai;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Anthropic Claude backend ({@code POST /v1/messages}).
 *
 * <p>Wire format per the official Messages API reference: {@code x-api-key} +
 * {@code anthropic-version: 2023-06-01} headers; body carries {@code model}, {@code max_tokens},
 * a top-level {@code system} prompt and a single user message. The response's {@code content} is
 * an array of blocks — the reply is the first {@code text} block. {@code stop_reason} may be
 * {@code "refusal"}, which we surface as an error instead of an empty reply. Chat replies are
 * short, so effort is set to {@code low} for speed.
 */
public class ClaudeBackend implements AiBackend {

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final String apiKey;
    private final String model;

    /**
     * Small, fast and cheap ($1 / $5 per million input / output tokens) — chat answers are one
     * line, so this is the sensible default. Alias rather than the dated id, so it follows the
     * current snapshot.
     */
    private static final String DEFAULT_MODEL = "claude-haiku-4-5";

    public ClaudeBackend(String apiKey, String model) {
        this.apiKey = apiKey;
        this.model = model == null || model.isBlank() ? DEFAULT_MODEL : model;
    }

    /**
     * {@code output_config.effort} errors on Haiku 4.5 and Sonnet 4.5, so it is sent per-model
     * rather than always. Omitting it just means the model runs at its default effort.
     */
    private static boolean supportsEffort(String model) {
        return model.startsWith("claude-opus-5") || model.startsWith("claude-opus-4-8")
                || model.startsWith("claude-opus-4-7") || model.startsWith("claude-opus-4-6")
                || model.startsWith("claude-sonnet-5") || model.startsWith("claude-sonnet-4-6")
                || model.startsWith("claude-fable-5");
    }

    @Override
    public CompletableFuture<String> complete(String systemPrompt, List<ChatMessage> history) {
        JsonObject body = new JsonObject();
        body.addProperty("model", model);
        body.addProperty("max_tokens", 2000);
        body.addProperty("system", systemPrompt);

        if (supportsEffort(model)) {
            JsonObject outputConfig = new JsonObject();
            outputConfig.addProperty("effort", "low");
            body.add("output_config", outputConfig);
        }

        // The Messages API requires the conversation to start with a user turn, so drop any
        // assistant turns that ended up at the front of the history window.
        int start = 0;
        while (start < history.size() && !history.get(start).isUser()) {
            start++;
        }

        JsonArray messages = new JsonArray();
        for (ChatMessage turn : history.subList(start, history.size())) {
            JsonObject entry = new JsonObject();
            entry.addProperty("role", turn.role());
            entry.addProperty("content", turn.content());
            messages.add(entry);
        }
        if (messages.isEmpty()) {
            return CompletableFuture.failedFuture(new RuntimeException("nothing to send"));
        }
        body.add("messages", messages);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.anthropic.com/v1/messages"))
                .timeout(Duration.ofSeconds(60))
                .header("Content-Type", "application/json")
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                .build();

        return HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .thenApply(resp -> {
                    if (resp.statusCode() / 100 != 2) {
                        throw new RuntimeException(explainError(resp.statusCode(), resp.body()));
                    }
                    JsonObject json = JsonParser.parseString(resp.body()).getAsJsonObject();
                    if (json.has("stop_reason") && !json.get("stop_reason").isJsonNull()
                            && "refusal".equals(json.get("stop_reason").getAsString())) {
                        throw new RuntimeException("Claude refused the request");
                    }
                    JsonArray content = json.getAsJsonArray("content");
                    for (var el : content) {
                        JsonObject block = el.getAsJsonObject();
                        if ("text".equals(block.get("type").getAsString())) {
                            return block.get("text").getAsString();
                        }
                    }
                    throw new RuntimeException("Claude returned no text");
                });
    }

    /**
     * Turns an error response into something a player can act on.
     *
     * <p>"HTTP 401" says nothing while you are setting the key up. The API answers with
     * {@code {"error": {"type": ..., "message": ...}}}, and its message names the real cause —
     * wrong key, no credit left, unknown model id — so it is worth showing in chat.
     */
    private static String explainError(int status, String body) {
        String detail = "";
        try {
            JsonObject error = JsonParser.parseString(body).getAsJsonObject()
                    .getAsJsonObject("error");
            if (error != null && error.has("message")) {
                detail = ": " + error.get("message").getAsString();
            }
        } catch (RuntimeException ignored) {
            // Not JSON (a proxy or gateway error page): the status code is all we have.
        }
        String hint = switch (status) {
            case 401 -> " (check the API key)";
            case 400 -> " (check the model id)";
            case 402 -> " (no credit left on the account)";
            case 429 -> " (rate limited, try again in a moment)";
            default -> "";
        };
        return "Claude HTTP " + status + hint + detail;
    }
}
