package dev.vaijanath.aiagent.audit;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Delivers audit events to a delegate sink on a background thread, so a slow or hanging sink (e.g. an
 * fsync) never delays the request that produced the event — use this when a request deadline must
 * cover the entire API call, not just the model/tool work. {@link #record} is non-blocking: it
 * enqueues onto a bounded buffer and returns immediately, dropping (and counting) events if the
 * buffer is full rather than blocking the caller.
 *
 * <p>Trade-off vs. a synchronous sink: bounded latency, but events can be dropped under sustained
 * backpressure or lost on a crash before they are drained. {@link #close()} flushes and stops; wrap a
 * durable sink (e.g. {@code FileAuditSink}) as the delegate when you need both.
 */
public final class AsyncAuditSink implements AuditSink, AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(AsyncAuditSink.class);
    private static final int DEFAULT_CAPACITY = 10_000;
    private static final Duration DRAIN_TIMEOUT = Duration.ofSeconds(5);

    private final AuditSink delegate;
    private final BlockingQueue<AuditEvent> queue;
    private final Thread worker;
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final AtomicLong dropped = new AtomicLong();

    public AsyncAuditSink(AuditSink delegate) {
        this(delegate, DEFAULT_CAPACITY);
    }

    public AsyncAuditSink(AuditSink delegate, int capacity) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.queue = new ArrayBlockingQueue<>(Math.max(1, capacity));
        this.worker = Thread.ofVirtual().name("audit-writer").start(this::drain);
    }

    @Override
    public void record(AuditEvent event) {
        if (!queue.offer(event)) {
            long n = dropped.incrementAndGet();
            if (n == 1 || n % 1000 == 0) {
                log.warn("audit buffer full; dropped {} event(s) so far", n);
            }
        }
    }

    /** Number of events dropped because the buffer was full. */
    public long droppedCount() {
        return dropped.get();
    }

    private void drain() {
        while (running.get() || !queue.isEmpty()) {
            try {
                AuditEvent event = queue.poll(100, TimeUnit.MILLISECONDS);
                if (event != null) {
                    deliver(event);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        // Best-effort flush of anything left after an interrupt.
        List<AuditEvent> remaining = new ArrayList<>();
        queue.drainTo(remaining);
        remaining.forEach(this::deliver);
    }

    private void deliver(AuditEvent event) {
        try {
            delegate.record(event);
        } catch (RuntimeException e) {
            log.warn("audit delegate threw; dropping '{}' event", event.type(), e);
        }
    }

    /** Stops accepting work and waits (bounded) for the buffer to drain to the delegate. */
    @Override
    public void close() {
        running.set(false);
        try {
            worker.join(DRAIN_TIMEOUT);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
