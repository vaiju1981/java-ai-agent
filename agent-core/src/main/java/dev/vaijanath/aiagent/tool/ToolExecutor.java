package dev.vaijanath.aiagent.tool;

/**
 * How a tool call is turned into a result. The runtime's default path authorizes and invokes the
 * real tool; a {@link ReplayToolExecutor} instead returns previously recorded results, so a recorded
 * run can be replayed without re-executing tools (and re-triggering their side effects).
 */
public interface ToolExecutor {

    ToolResult execute(String toolName, String argumentsJson);
}
