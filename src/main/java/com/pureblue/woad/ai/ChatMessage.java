package com.pureblue.woad.ai;

/**
 * One turn of conversation sent to the model.
 *
 * @param role    {@code "user"} for something a player said, {@code "assistant"} for our own reply
 * @param content the text of that turn
 */
public record ChatMessage(String role, String content) {

    public static ChatMessage user(String content) {
        return new ChatMessage("user", content);
    }

    public static ChatMessage assistant(String content) {
        return new ChatMessage("assistant", content);
    }

    public boolean isUser() {
        return "user".equals(role);
    }
}
