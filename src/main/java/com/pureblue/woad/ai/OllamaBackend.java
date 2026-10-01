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
 * Local Ollama backend ({@code POST <base>/api/chat}, non-streaming).
 *
 * <p>The first request after Ollama starts can be slow (model load), hence the generous timeout.
 *
 * <p>Tools use the same function shape as OpenAI. Ollama returns the calls in
 * {@code message.tool_calls} with the arguments already parsed, and expects each result back as a
 * {@code tool} message. Models without tool support are refused with HTTP 400 "does not support
 * tools", reported as {@link ToolsUnsupportedException} so the caller can answer without them.
 */
public class OllamaBackend implements AiBackend {

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final String baseUrl;
    private final String model;
    private final int keepAliveMinutes;

    public OllamaBackend(String baseUrl, String model, int keepAliveMinutes) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.model = model == null || model.isBlank() ? "llama3.2" : model;
        this.keepAliveMinutes = keepAliveMinutes;
    }

    @Override
    public CompletableFuture<String> complete(String systemPrompt, List<ChatMessage> history, List<AiTool> tools) {
        JsonArray messages = new JsonArray();
        messages.add(msg("system", systemPrompt));
        for (ChatMessage turn : history) {
            messages.add(msg(turn.role(), turn.content()));
        }
        return CompletableFuture.supplyAsync(() -> exchange(messages, tools), WORKER);
    }

    private String exchange(JsonArray messages, List<AiTool> tools) {
        for (int round = 0; ; round++) {
            boolean offerTools = !tools.isEmpty() && round < MAX_TOOL_ROUNDS;
            JsonObject json = send(body(messages, offerTools ? tools : List.of()));
            JsonObject message = json.getAsJsonObject("message");

            JsonArray calls = message.has("tool_calls") && message.get("tool_calls").isJsonArray()
                    ? message.getAsJsonArray("tool_calls") : null;
            if (calls != null && !calls.isEmpty()) {
                messages.add(message);
                for (JsonElement element : calls) {
                    JsonObject function = element.getAsJsonObject().getAsJsonObject("function");
                    String name = function.get("name").getAsString();
                    JsonElement raw = function.get("arguments");
                    JsonObject arguments = raw != null && raw.isJsonObject() ? raw.getAsJsonObject() : parse(raw);
                    JsonObject result = msg("tool", AiBackend.run(tools, name, arguments));
                    result.addProperty("tool_name", name);
                    messages.add(result);
                }
                continue;
            }

            String content = message.has("content") && !message.get("content").isJsonNull()
                    ? message.get("content").getAsString() : "";
            // Older Ollama builds ignore "think": false — fall back to the reasoning text
            // so the player gets something rather than silence.
            if (content.isBlank() && message.has("thinking")) {
                content = message.get("thinking").getAsString();
            }
            if (content.isBlank()) {
                String reason = json.has("done_reason") ? json.get("done_reason").getAsString() : "?";
                throw new RuntimeException("empty reply from " + model + " (done_reason=" + reason + ")");
            }
            // Some models still wrap reasoning in <think>...</think> inside the content.
            return content.replaceAll("(?s)<think>.*?</think>", "").trim();
        }
    }

    private JsonObject body(JsonArray messages, List<AiTool> tools) {
        JsonObject body = new JsonObject();
        body.addProperty("model", model);
        body.addProperty("stream", false);
        // Reasoning models (qwen3, deepseek-r1, ...) otherwise spend the whole token budget in a
        // separate "thinking" field and return an EMPTY "content". Chat answers are one line, so
        // the reasoning is not worth the tokens or the latency.
        body.addProperty("think", false);
        // Ollama unloads the model after 5 minutes of inactivity by default, so the first question
        // after a quiet stretch pays the whole load time again. -1 pins it in memory for good.
        if (keepAliveMinutes < 0) {
            body.addProperty("keep_alive", -1);
        } else {
            body.addProperty("keep_alive", keepAliveMinutes + "m");
        }
        body.add("messages", messages);
        if (!tools.isEmpty()) body.add("tools", OpenAiBackend.functionTools(tools));

        JsonObject options = new JsonObject();
        options.addProperty("num_predict", 400);
        body.add("options", options);
        return body;
    }

    private JsonObject send(JsonObject body) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/chat"))
                .timeout(Duration.ofSeconds(90))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> resp;
        try {
            resp = HTTP.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (java.io.IOException e) {
            throw new RuntimeException("could not reach Ollama at " + baseUrl, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("interrupted", e);
        }
        if (resp.statusCode() / 100 != 2) {
            String error = resp.body() == null ? "" : resp.body();
            if (resp.statusCode() == 400 && error.contains("does not support tools")) {
                throw new ToolsUnsupportedException(model + " does not support tools");
            }
            throw new RuntimeException("Ollama HTTP " + resp.statusCode());
        }
        return JsonParser.parseString(resp.body()).getAsJsonObject();
    }

    private static JsonObject parse(JsonElement raw) {
        try {
            return JsonParser.parseString(raw.getAsString()).getAsJsonObject();
        } catch (RuntimeException e) {
            return new JsonObject();
        }
    }

    private static JsonObject msg(String role, String content) {
        JsonObject m = new JsonObject();
        m.addProperty("role", role);
        m.addProperty("content", content);
        return m;
    }
}
