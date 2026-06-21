package dev.vaijanath.aiagent.model;

import java.util.List;

/** A model's reply: free text and/or a set of tool calls to execute. */
public record ModelResponse(String text, List<ToolCall> toolCalls) {

    public ModelResponse {
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
    }

    public static ModelResponse text(String text) {
        return new ModelResponse(text, List.of());
    }

    public boolean hasToolCalls() {
        return !toolCalls.isEmpty();
    }
}
