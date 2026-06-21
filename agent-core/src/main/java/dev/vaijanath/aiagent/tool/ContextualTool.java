package dev.vaijanath.aiagent.tool;

/** A tool that receives the full governed invocation context, not only a JSON argument string. */
public interface ContextualTool extends Tool {

    ToolResult invoke(ToolInvocation invocation);

    /** Direct ungoverned invocation is deliberately unsupported for context-dependent tools. */
    @Override
    default ToolResult invoke(String argumentsJson) {
        return ToolResult.error("contextual tool must be invoked through an agent runtime");
    }
}
