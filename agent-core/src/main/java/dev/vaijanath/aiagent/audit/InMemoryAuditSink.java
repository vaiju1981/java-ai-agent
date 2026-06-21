package dev.vaijanath.aiagent.audit;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Keeps audit events in memory — for tests and inspection. Safe for concurrent writes. */
public final class InMemoryAuditSink implements AuditSink {

    private final List<AuditEvent> events = new CopyOnWriteArrayList<>();

    @Override
    public void record(AuditEvent event) {
        events.add(event);
    }

    /** A snapshot of the events recorded so far. */
    public List<AuditEvent> events() {
        return List.copyOf(events);
    }
}
