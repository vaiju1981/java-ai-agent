package dev.vaijanath.aiagent.model;

import java.util.Objects;

/** A single chat message. */
public record Message(Role role, String content) {

    public Message {
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(content, "content");
    }

    public static Message system(String content) {
        return new Message(Role.SYSTEM, content);
    }

    public static Message user(String content) {
        return new Message(Role.USER, content);
    }

    public static Message assistant(String content) {
        return new Message(Role.ASSISTANT, content);
    }

    public static Message tool(String content) {
        return new Message(Role.TOOL, content);
    }
}
