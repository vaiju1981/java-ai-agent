package dev.vaijanath.aiagent.audit;

import java.time.Instant;
import java.util.UUID;

/**
 * One durable record of something the runtime did, carrying the identity and correlation needed to
 * reconstruct who did what, when, and under which trace. Unlike best-effort {@code AgentObserver}
 * telemetry, audit events are emitted by the runtime itself (which holds the request context) and are
 * meant to be persisted.
 *
 * <p>Details are kept non-sensitive on purpose (names, decisions, reasons, sizes) — not raw user
 * content or tool arguments, which may contain PII.
 *
 * @param eventId   a unique id for this event
 * @param at        when it happened
 * @param type      a stable event type, e.g. {@code "turn.start"}, {@code "tool.denied"}
 * @param traceId   correlation id shared across a turn and its sub-agents
 * @param sessionId the conversation this belongs to
 * @param principal who the turn was for
 * @param tenant    the tenant/organization
 * @param detail    a short, non-sensitive description
 */
public record AuditEvent(
        String eventId,
        Instant at,
        String type,
        String traceId,
        String sessionId,
        String principal,
        String tenant,
        String detail) {

    /** Builds an event stamped with a fresh id and the current time. */
    public static AuditEvent now(
            String type, String traceId, String sessionId, String principal, String tenant, String detail) {
        return new AuditEvent(
                UUID.randomUUID().toString(), Instant.now(), type, traceId, sessionId, principal, tenant, detail);
    }
}
