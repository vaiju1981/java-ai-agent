package dev.vaijanath.aiagent.audit;

/**
 * Where audit events are durably delivered. Implementations must be safe for concurrent calls and
 * should not throw into the caller — auditing must never break a run; persist or drop, but do it
 * reliably for the chosen backing store.
 *
 * <p>A sink runs on the request thread by default. Wrap one in {@link AsyncAuditSink} to deliver
 * events off the request path when a deadline must cover the whole call (a slow fsync then can't
 * delay the request).
 */
public interface AuditSink {

    void record(AuditEvent event);

    /** A sink that discards events — the default when no audit is configured. */
    static AuditSink none() {
        return event -> {};
    }
}
