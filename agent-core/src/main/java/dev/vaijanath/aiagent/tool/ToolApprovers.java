package dev.vaijanath.aiagent.tool;

import java.util.Set;

/** Common {@link ToolApprover} policies. */
public final class ToolApprovers {

    private ToolApprovers() {}

    /** Permits every tool (the convenient, ungoverned default). */
    public static ToolApprover allowAll() {
        return call -> ToolDecision.allow();
    }

    /** Permits only the named tools; denies anything else. */
    public static ToolApprover allowList(String... toolNames) {
        Set<String> allowed = Set.of(toolNames);
        return call -> allowed.contains(call.toolName())
                ? ToolDecision.allow()
                : ToolDecision.deny("'" + call.toolName() + "' is not on the allow-list");
    }

    /**
     * The safe default for a governed agent: read-only tools run, but an effectful tool is denied
     * unless explicitly named. A tool of unspecified {@link ToolEffect} counts as effectful.
     */
    public static ToolApprover denyEffectful(String... allowedEffectfulTools) {
        Set<String> allowed = Set.of(allowedEffectfulTools);
        return call -> (call.spec().effect() == ToolEffect.READ_ONLY || allowed.contains(call.toolName()))
                ? ToolDecision.allow()
                : ToolDecision.deny("effectful tool '" + call.toolName() + "' requires explicit authorization");
    }
}
