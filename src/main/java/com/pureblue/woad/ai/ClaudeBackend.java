package com.pureblue.woad.ai;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
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
 * a top-level {@code system} prompt, the conversation and the {@code tools}. The response's
 * {@code content} is an array of blocks. When {@code stop_reason} is {@code "tool_use"} the
 * {@code tool_use} blocks are run and answered with {@code tool_result} blocks in a user turn, and
 * the request is sent again; otherwise the reply is the text blocks. {@code stop_reason} may be
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
    public CompletableFuture<String> complete(String systemPrompt, List<ChatMessage> history, List<AiTool> tools) {
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
        return CompletableFuture.supplyAsync(() -> exchange(systemPrompt, messages, tools), WORKER);
    }

    /** Sends the conversation, runs whatever tools the model calls, and returns its final text. */
    private String exchange(String systemPrompt, JsonArray messages, List<AiTool> tools) {
        for (int round = 0; ; round++) {
            // On the last round the tools are withheld, so the model has to answer with what it has.
            boolean offerTools = !tools.isEmpty() && round < MAX_TOOL_ROUNDS;
            JsonObject json = send(body(systemPrompt, messages, offerTools ? tools : List.of()));

            String stop = json.has("stop_reason") && !json.get("stop_reason").isJsonNull()
                    ? json.get("stop_reason").getAsString() : "";
            if ("refusal".equals(stop)) throw new RuntimeException("Claude refused the request");

            JsonArray content = json.getAsJsonArray("content");
            if (!"tool_use".equals(stop)) return text(content);

            // The assistant turn goes back verbatim — tool_use blocks included — followed by one
            // user turn carrying a tool_result for each of them, in the same order.
            JsonObject assistant = new JsonObject();
            assistant.addProperty("role", "assistant");
            assistant.add("content", content);
            messages.add(assistant);

            JsonArray results = new JsonArray();
            for (JsonElement element : content) {
                JsonObject block = element.getAsJsonObject();
                if (!"tool_use".equals(block.get("type").getAsString())) continue;
                JsonObject input = block.has("input") && block.get("input").isJsonObject()
                        ? block.getAsJsonObject("input") : new JsonObject();
                JsonObject result = new JsonObject();
                result.addProperty("type", "tool_result");
                result.addProperty("tool_use_id", block.get("id").getAsString());
                result.addProperty("content", AiBackend.run(tools, block.get("name").getAsString(), input));
                results.add(result);
            }
            JsonObject user = new JsonObject();
            user.addProperty("role", "user");
            user.add("content", results);
            messages.add(user);
        }
    }

    private JsonObject body(String systemPrompt, JsonArray messages, List<AiTool> tools) {
        JsonObject body = new JsonObject();
        body.addProperty("model", model);
        body.addProperty("max_tokens", 2000);
        body.addProperty("system", systemPrompt);
        if (supportsEffort(model)) {
            JsonObject outputConfig = new JsonObject();
            outputConfig.addProperty("effort", "low");
            body.add("output_config", outputConfig);
        }
        if (!tools.isEmpty()) {
            JsonArray list = new JsonArray();
            for (AiTool tool : tools) {
                JsonObject entry = new JsonObject();
                entry.addProperty("name", tool.name());
                entry.addProperty("description", tool.description());
                entry.add("input_schema", tool.schema());
                list.add(entry);
            }
            body.add("tools", list);
        }
        body.add("messages", messages);
        return body;
    }

    private JsonObject send(JsonObject body) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.anthropic.com/v1/messages"))
                .timeout(Duration.ofSeconds(60))
                .header("Content-Type", "application/json")
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> resp;
        try {
            resp = HTTP.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (java.io.IOException e) {
            throw new RuntimeException("could not reach Claude: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("interrupted", e);
        }
        if (resp.statusCode() / 100 != 2) {
            throw new RuntimeException(explainError(resp.statusCode(), resp.body()));
        }
        return JsonParser.parseString(resp.body()).getAsJsonObject();
    }

    /** The reply: every text block, joined. A tool-using turn can split its prose around the calls. */
    private static String text(JsonArray content) {
        StringBuilder out = new StringBuilder();
        for (JsonElement element : content) {
            JsonObject block = element.getAsJsonObject();
            if ("text".equals(block.get("type").getAsString())) {
                if (!out.isEmpty()) out.append(' ');
                out.append(block.get("text").getAsString());
            }
        }
        if (out.isEmpty()) throw new RuntimeException("Claude returned no text");
        return out.toString();
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
