package dev.vaijanath.aiagent.tool;

import java.util.Set;

/** Common {@link ToolApprover} policies. */
public final class ToolApprovers {

    private ToolApprovers() {}

    /** Permits every tool (the default). */
    public static ToolApprover allowAll() {
        return (toolName, argumentsJson) -> ToolDecision.allow();
    }

    /** Permits only the named tools; denies anything else. */
    public static ToolApprover allowList(String... toolNames) {
        Set<String> allowed = Set.of(toolNames);
        return (toolName, argumentsJson) -> allowed.contains(toolName)
                ? ToolDecision.allow()
                : ToolDecision.deny("'" + toolName + "' is not on the allow-list");
    }
}
