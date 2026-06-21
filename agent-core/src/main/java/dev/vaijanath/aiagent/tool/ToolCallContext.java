package dev.vaijanath.aiagent.tool;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
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
        Instant deadline,
        String clientIdempotencyKey) {

    public ToolCallContext {
        Objects.requireNonNull(spec, "spec");
        argumentsJson = argumentsJson == null ? "{}" : argumentsJson;
        principal = (principal == null || principal.isBlank()) ? "anonymous" : principal;
        tenant = (tenant == null || tenant.isBlank()) ? "default" : tenant;
    }

    /** Compatibility constructor for callers that do not supply a client idempotency key. */
    public ToolCallContext(
            ToolSpec spec,
            String argumentsJson,
            String principal,
            String tenant,
            String traceId,
            String sessionId,
            Instant deadline) {
        this(spec, argumentsJson, principal, tenant, traceId, sessionId, deadline, null);
    }

    public String toolName() {
        return spec.name();
    }

    public Optional<Instant> deadlineAt() {
        return Optional.ofNullable(deadline);
    }

    /**
     * A stable opaque key for this logical call. When the application supplied a client idempotency
     * key, retries remain stable across traces and service instances; otherwise the key is scoped to
     * this trace/session. The SHA-256 digest does not expose the caller key or arguments.
     */
    public String idempotencyKey() {
        boolean hasClientKey = clientIdempotencyKey != null && !clientIdempotencyKey.isBlank();
        String requestScope = hasClientKey
                ? part(clientIdempotencyKey)
                : part(sessionId) + part(traceId) + part(argumentsJson);
        String material = part(tenant) + requestScope + part(spec.name());
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(material.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private static String part(String value) {
        String v = value == null ? "" : value;
        return v.length() + ":" + v;
    }
}
