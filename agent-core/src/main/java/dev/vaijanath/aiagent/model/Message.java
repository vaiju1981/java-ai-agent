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
 *   <li>a {@code TOOL} message carries the {@code toolCallId} and {@code toolName} it answers;</li>
 *   <li>a {@code USER} message may carry {@code media} (images/audio) for multimodal models.</li>
 * </ul>
 */
public record Message(
        Role role,
        String content,
        List<ToolCall> toolCalls,
        String toolCallId,
        String toolName,
        List<Media> media) {

    public Message {
        Objects.requireNonNull(role, "role");
        content = content == null ? "" : content;
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
        media = media == null ? List.of() : List.copyOf(media);
    }

    public static Message system(String content) {
        return new Message(Role.SYSTEM, content, List.of(), null, null, List.of());
    }

    public static Message user(String content) {
        return new Message(Role.USER, content, List.of(), null, null, List.of());
    }

    /** A user turn that carries {@link Media} (images/audio) alongside the text, for vision/audio models. */
    public static Message user(String content, List<Media> media) {
        return new Message(Role.USER, content, List.of(), null, null, media);
    }

    public static Message assistant(String content) {
        return new Message(Role.ASSISTANT, content, List.of(), null, null, List.of());
    }

    /** An assistant turn that requests one or more tool calls. */
    public static Message assistant(String content, List<ToolCall> toolCalls) {
        return new Message(Role.ASSISTANT, content, toolCalls, null, null, List.of());
    }

    /** The result of executing a tool, linked back to the originating tool call. */
    public static Message toolResult(String toolCallId, String toolName, String content) {
        return new Message(Role.TOOL, content, List.of(), toolCallId, toolName, List.of());
    }

    public boolean hasToolCalls() {
        return !toolCalls.isEmpty();
    }

    /** True when this message carries non-text {@link Media} parts (images/audio). */
    public boolean hasMedia() {
        return !media.isEmpty();
    }
}
