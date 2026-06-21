package dev.vaijanath.aiagent.tool;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * What a {@link ToolApprover} sees when deciding whether a tool call may run: the tool's
 * {@link ToolSpec} (so its capability/effect is known), the raw arguments, who is asking (principal,
 * tenant), and the call's correlation (traceId, sessionId) and deadline. Richer context than a bare
 * name means policies can decide by capability, identity, trace, or remaining time.
 */
public record ToolCallContext(
        ToolSpec spec,
        String argumentsJson,
        String principal,
        String tenant,
        String traceId,
        String sessionId,
        Instant deadline) {

    public ToolCallContext {
        Objects.requireNonNull(spec, "spec");
        argumentsJson = argumentsJson == null ? "{}" : argumentsJson;
        principal = (principal == null || principal.isBlank()) ? "anonymous" : principal;
        tenant = (tenant == null || tenant.isBlank()) ? "default" : tenant;
    }

    public String toolName() {
        return spec.name();
    }

    public Optional<Instant> deadlineAt() {
        return Optional.ofNullable(deadline);
    }
}
