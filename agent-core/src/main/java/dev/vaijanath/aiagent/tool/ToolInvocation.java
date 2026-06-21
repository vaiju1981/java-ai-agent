package dev.vaijanath.aiagent.tool;

import dev.vaijanath.aiagent.model.ToolCall;
import java.util.Objects;

/**
 * A governed tool invocation: the model's call plus the validated identity, deadline, correlation,
 * and idempotency context established by the runtime. Effectful production tools should implement
 * {@link ContextualTool} so they do not need thread-locals or side channels to access this data.
 */
public record ToolInvocation(ToolCall call, ToolCallContext context) {

    public ToolInvocation {
        Objects.requireNonNull(call, "call");
        Objects.requireNonNull(context, "context");
    }

    public String argumentsJson() {
        return call.argumentsJson();
    }

    public String idempotencyKey() {
        return context.idempotencyKey();
    }
}
