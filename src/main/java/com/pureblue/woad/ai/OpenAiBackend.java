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
 * OpenAI backend ({@code POST /v1/chat/completions} with a Bearer API key).
 *
 * <p>Tools are sent as {@code function} tools. When the reply carries {@code tool_calls}, the
 * assistant message goes back as it came, followed by one {@code tool} message per call, and the
 * request is repeated until the model answers in text.
 */
public class OpenAiBackend implements AiBackend {

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final String apiKey;
    private final String model;

    public OpenAiBackend(String apiKey, String model) {
        this.apiKey = apiKey;
        this.model = model == null || model.isBlank() ? "gpt-4o-mini" : model;
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
            JsonObject body = new JsonObject();
            body.addProperty("model", model);
            // max_tokens is refused by the reasoning models; this one is accepted by all of them.
            body.addProperty("max_completion_tokens", 1000);
            body.add("messages", messages);
            if (offerTools) body.add("tools", functionTools(tools));

            JsonObject message = send(body).getAsJsonArray("choices").get(0).getAsJsonObject()
                    .getAsJsonObject("message");
            JsonArray calls = message.has("tool_calls") && message.get("tool_calls").isJsonArray()
                    ? message.getAsJsonArray("tool_calls") : null;
            if (calls == null || calls.isEmpty()) {
                if (!message.has("content") || message.get("content").isJsonNull()) {
                    throw new RuntimeException("OpenAI returned no text");
                }
                return message.get("content").getAsString();
            }

            messages.add(message);
            for (JsonElement element : calls) {
                JsonObject call = element.getAsJsonObject();
                JsonObject function = call.getAsJsonObject("function");
                JsonObject arguments;
                try {
                    arguments = JsonParser.parseString(function.get("arguments").getAsString()).getAsJsonObject();
                } catch (RuntimeException e) {
                    arguments = new JsonObject();
                }
                JsonObject result = new JsonObject();
                result.addProperty("role", "tool");
                result.addProperty("tool_call_id", call.get("id").getAsString());
                result.addProperty("content", AiBackend.run(tools, function.get("name").getAsString(), arguments));
                messages.add(result);
            }
        }
    }

    /** The {@code tools} array in the function-calling shape OpenAI and Ollama share. */
    static JsonArray functionTools(List<AiTool> tools) {
        JsonArray list = new JsonArray();
        for (AiTool tool : tools) {
            JsonObject function = new JsonObject();
            function.addProperty("name", tool.name());
            function.addProperty("description", tool.description());
            function.add("parameters", tool.schema());
            JsonObject entry = new JsonObject();
            entry.addProperty("type", "function");
            entry.add("function", function);
            list.add(entry);
        }
        return list;
    }

    private JsonObject send(JsonObject body) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.openai.com/v1/chat/completions"))
                .timeout(Duration.ofSeconds(45))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> resp;
        try {
            resp = HTTP.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (java.io.IOException e) {
            throw new RuntimeException("could not reach OpenAI: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("interrupted", e);
        }
        if (resp.statusCode() / 100 != 2) {
            throw new RuntimeException("OpenAI HTTP " + resp.statusCode() + errorDetail(resp.body()));
        }
        return JsonParser.parseString(resp.body()).getAsJsonObject();
    }

    /** OpenAI explains its errors in {@code error.message}; worth showing while setting up. */
    private static String errorDetail(String body) {
        try {
            JsonObject error = JsonParser.parseString(body).getAsJsonObject().getAsJsonObject("error");
            if (error != null && error.has("message")) return ": " + error.get("message").getAsString();
        } catch (RuntimeException ignored) {
            // not JSON
        }
        return "";
    }

    private static JsonObject msg(String role, String content) {
        JsonObject m = new JsonObject();
        m.addProperty("role", role);
        m.addProperty("content", content);
        return m;
    }
}
