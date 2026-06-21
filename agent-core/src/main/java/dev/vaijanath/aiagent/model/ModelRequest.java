package dev.vaijanath.aiagent.model;

import dev.vaijanath.aiagent.tool.ToolSpec;
import java.util.List;

/** A request to a {@link ModelPort}: the conversation so far plus any tools the model may call. */
public record ModelRequest(List<Message> messages, List<ToolSpec> tools) {

    public ModelRequest {
        messages = List.copyOf(messages);
        tools = tools == null ? List.of() : List.copyOf(tools);
    }

    public static ModelRequest of(List<Message> messages) {
        return new ModelRequest(messages, List.of());
    }
}
