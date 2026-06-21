package dev.vaijanath.aiagent.tool;

/** The outcome of invoking a {@link Tool}. */
public record ToolResult(String content, boolean error) {

    public static ToolResult ok(String content) {
        return new ToolResult(content, false);
    }

    public static ToolResult error(String content) {
        return new ToolResult(content, true);
    }
}
