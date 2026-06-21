package dev.vaijanath.aiagent.model;

import java.util.List;
import java.util.Objects;

/**
 * A single chat message.
 *
 * <p>Most messages are plain text. Two carry extra structure so a tool-calling conversation can be
 * replayed faithfully to a model:
 *
 * <ul>
 *   <li>an {@code ASSISTANT} message may carry {@code toolCalls} (the model's request to run tools);</li>
 *   <li>a {@code TOOL} message carries the {@code toolCallId} and {@code toolName} it answers.</li>
 * </ul>
 */
public record Message(
        Role role,
        String content,
        List<ToolCall> toolCalls,
        String toolCallId,
        String toolName) {

    public Message {
        Objects.requireNonNull(role, "role");
        content = content == null ? "" : content;
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
    }

    public static Message system(String content) {
        return new Message(Role.SYSTEM, content, List.of(), null, null);
    }

    public static Message user(String content) {
        return new Message(Role.USER, content, List.of(), null, null);
    }

    public static Message assistant(String content) {
        return new Message(Role.ASSISTANT, content, List.of(), null, null);
    }

    /** An assistant turn that requests one or more tool calls. */
    public static Message assistant(String content, List<ToolCall> toolCalls) {
        return new Message(Role.ASSISTANT, content, toolCalls, null, null);
    }

    /** The result of executing a tool, linked back to the originating tool call. */
    public static Message toolResult(String toolCallId, String toolName, String content) {
        return new Message(Role.TOOL, content, List.of(), toolCallId, toolName);
    }

    public boolean hasToolCalls() {
        return !toolCalls.isEmpty();
    }
}
