package dev.vaijanath.aiagent.tool;

/** An authorization decision for a single tool call. */
public record ToolDecision(boolean allowed, String reason) {

    public static ToolDecision allow() {
        return new ToolDecision(true, "");
    }

    public static ToolDecision deny(String reason) {
        return new ToolDecision(false, reason);
    }
}
