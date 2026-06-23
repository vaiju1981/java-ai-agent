package dev.vaijanath.aiagent.tool;

/**
 * A {@link ContextualTool} that, alongside the text result the model sees, can emit a structured JSON
 * payload for observers and UIs (e.g. to render the tool's output inline as a table or chart). The
 * payload is produced in the same invocation as the text — no double execution — and is never sent to
 * the model. A runtime that doesn't understand structured results still works correctly: it calls
 * {@link #invoke(ToolInvocation)}, which returns just the model-facing {@link ToolResult}.
 */
public interface StructuredTool extends ContextualTool {

    /** Invokes the tool, returning both the model-facing result and an optional structured payload. */
    StructuredToolResult invokeStructured(ToolInvocation invocation);

    @Override
    default ToolResult invoke(ToolInvocation invocation) {
        return invokeStructured(invocation).result();
    }
}
