package dev.vaijanath.aiagent.model;

import java.util.List;

/** A model's reply: free text and/or tool calls, plus any reported token usage. */
public record ModelResponse(String text, List<ToolCall> toolCalls, Usage usage) {

    public ModelResponse {
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
        usage = usage == null ? Usage.UNKNOWN : usage;
    }

    /** Convenience overload for responses without reported usage. */
    public ModelResponse(String text, List<ToolCall> toolCalls) {
        this(text, toolCalls, Usage.UNKNOWN);
    }

    public static ModelResponse text(String text) {
        return new ModelResponse(text, List.of(), Usage.UNKNOWN);
    }

    public static ModelResponse text(String text, Usage usage) {
        return new ModelResponse(text, List.of(), usage);
    }

    public boolean hasToolCalls() {
        return !toolCalls.isEmpty();
    }
}
