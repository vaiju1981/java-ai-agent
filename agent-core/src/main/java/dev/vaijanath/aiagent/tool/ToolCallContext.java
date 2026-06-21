package dev.vaijanath.aiagent.tool;

import java.util.Objects;

/**
 * What a {@link ToolApprover} sees when deciding whether a tool call may run: the tool's
 * {@link ToolSpec} (so its capability/effect is known), the raw arguments, and who is asking
 * (principal and tenant). Richer context than a bare name means policies can deny by capability or
 * identity, not just by name.
 */
public record ToolCallContext(ToolSpec spec, String argumentsJson, String principal, String tenant) {

    public ToolCallContext {
        Objects.requireNonNull(spec, "spec");
        argumentsJson = argumentsJson == null ? "{}" : argumentsJson;
        principal = (principal == null || principal.isBlank()) ? "anonymous" : principal;
        tenant = (tenant == null || tenant.isBlank()) ? "default" : tenant;
    }

    public String toolName() {
        return spec.name();
    }
}
