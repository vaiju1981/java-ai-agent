package dev.vaijanath.aiagent.model;

import java.util.Objects;

/** A model's request to invoke a tool. {@code argumentsJson} is a JSON object literal. */
public record ToolCall(String id, String name, String argumentsJson) {

    public ToolCall {
        Objects.requireNonNull(name, "name");
        argumentsJson = (argumentsJson == null || argumentsJson.isBlank()) ? "{}" : argumentsJson;
    }
}
