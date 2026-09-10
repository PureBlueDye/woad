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
 * Local Ollama backend ({@code POST <base>/api/chat}, non-streaming).
 *
 * <p>The first request after Ollama starts can be slow (model load), hence the generous timeout.
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
    public CompletableFuture<String> complete(String systemPrompt, List<ChatMessage> history) {
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

        JsonArray messages = new JsonArray();
        messages.add(msg("system", systemPrompt));
        for (ChatMessage turn : history) {
            messages.add(msg(turn.role(), turn.content()));
        }
        body.add("messages", messages);

        JsonObject options = new JsonObject();
        options.addProperty("num_predict", 400);
        body.add("options", options);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/chat"))
                .timeout(Duration.ofSeconds(90))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                .build();

        return HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .thenApply(resp -> {
                    if (resp.statusCode() / 100 != 2) {
                        throw new RuntimeException("Ollama HTTP " + resp.statusCode());
                    }
                    JsonObject json = JsonParser.parseString(resp.body()).getAsJsonObject();
                    JsonObject message = json.getAsJsonObject("message");
                    String content = message.has("content") ? message.get("content").getAsString() : "";

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
                });
    }

    private static JsonObject msg(String role, String content) {
        JsonObject m = new JsonObject();
        m.addProperty("role", role);
        m.addProperty("content", content);
        return m;
    }
}
