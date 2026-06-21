package dev.vaijanath.aiagent.agent;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * The identity and governance context of a turn — who is asking, in which session and tenant, under
 * what trace and deadline. Conversation memory is scoped to {@link #sessionId()}, so one agent
 * instance can serve many sessions, users, and tenants concurrently without their histories
 * interleaving.
 *
 * @param sessionId  the conversation key; successive turns with the same id share memory
 * @param principal  who is making the request (defaults to {@code "anonymous"})
 * @param tenant     the tenant/organization for isolation (defaults to {@code "default"})
 * @param traceId    correlation id for audit and observability (defaults to the session id)
 * @param deadline   a hard wall-clock deadline for the turn, or {@code null} for none
 * @param attributes free-form context an application wants to carry (copied, never null)
 */
public record RequestContext(
        String sessionId,
        String principal,
        String tenant,
        String traceId,
        Instant deadline,
        Map<String, String> attributes) {

    public RequestContext {
        Objects.requireNonNull(sessionId, "sessionId");
        principal = (principal == null || principal.isBlank()) ? "anonymous" : principal;
        tenant = (tenant == null || tenant.isBlank()) ? "default" : tenant;
        traceId = (traceId == null || traceId.isBlank()) ? sessionId : traceId;
        attributes = (attributes == null) ? Map.of() : Map.copyOf(attributes);
    }

    /** A fresh, isolated session — the safe default for a one-off turn. */
    public static RequestContext ephemeral() {
        String id = UUID.randomUUID().toString();
        return new RequestContext(id, null, null, id, null, null);
    }

    /** A named session, so successive turns with the same id share conversation memory. */
    public static RequestContext session(String sessionId) {
        return new RequestContext(sessionId, null, null, null, null, null);
    }

    public Optional<Instant> deadlineAt() {
        return Optional.ofNullable(deadline);
    }

    /**
     * A context for a sub-agent: same identity, tenant, trace, and deadline, but a fresh session so
     * the sub-agent's conversation memory does not mix with the parent's.
     */
    public RequestContext childSession() {
        return new RequestContext(
                UUID.randomUUID().toString(), principal, tenant, traceId, deadline, attributes);
    }
}
