package com.pureblue.woad.ai;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.function.Function;

/**
 * Something the model may call while it answers: look an item up on Coflnet, run a party command…
 *
 * <p>The model decides when to call it and with what arguments; the mod runs it and hands the
 * result back, and the model then writes its answer from real data instead of guessing. This is
 * what lets players ask in their own words — understanding the sentence is the model's job, not a
 * list of keywords in the mod.
 *
 * @param name        identifier the model calls it by
 * @param description when and how to use it, written for the model
 * @param schema      JSON Schema of the arguments (an {@code object})
 * @param run         does the work; called off the client thread, returns the text the model reads
 */
public record AiTool(String name, String description, JsonObject schema, Function<JsonObject, String> run) {

    /** Runs the tool, turning a failure into a message the model can react to. */
    public String call(JsonObject arguments) {
        try {
            String result = run.apply(arguments == null ? new JsonObject() : arguments);
            return result == null || result.isBlank() ? "(no result)" : result;
        } catch (RuntimeException e) {
            return "Error: " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        }
    }

    // ---- Schema helpers ----------------------------------------------------------------------

    /** Starts an {@code object} schema; add properties with {@link #prop} and {@link #require}. */
    public static JsonObject newSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        schema.add("properties", new JsonObject());
        schema.add("required", new JsonArray());
        return schema;
    }

    /** Adds a property of a JSON type ({@code string}, {@code object}…) to an object schema. */
    public static JsonObject prop(JsonObject schema, String name, String type, String description) {
        JsonObject property = new JsonObject();
        property.addProperty("type", type);
        property.addProperty("description", description);
        schema.getAsJsonObject("properties").add(name, property);
        return property;
    }

    /** Adds a string property limited to the given values. */
    public static JsonObject choice(JsonObject schema, String name, String description, String... values) {
        JsonObject property = prop(schema, name, "string", description);
        JsonArray allowed = new JsonArray();
        for (String value : values) allowed.add(value);
        property.add("enum", allowed);
        return property;
    }

    public static JsonObject require(JsonObject schema, String... names) {
        JsonArray required = schema.getAsJsonArray("required");
        for (String name : names) required.add(name);
        return schema;
    }

    /** A string argument, or {@code ""} when the model left it out. */
    public static String string(JsonObject arguments, String name) {
        if (arguments == null || !arguments.has(name) || arguments.get(name).isJsonNull()) return "";
        return arguments.get(name).isJsonPrimitive()
                ? arguments.get(name).getAsString().trim()
                : arguments.get(name).toString();
    }
}
