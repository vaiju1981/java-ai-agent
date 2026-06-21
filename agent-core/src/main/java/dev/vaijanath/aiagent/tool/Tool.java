package dev.vaijanath.aiagent.tool;

/**
 * A capability the agent can invoke. Aligned with MCP so the same tool can later be exposed to, or
 * consumed from, any MCP-speaking substrate.
 */
public interface Tool {

    ToolSpec spec();

    ToolResult invoke(String argumentsJson);

    default String name() {
        return spec().name();
    }
}
