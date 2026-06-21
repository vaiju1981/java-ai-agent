package dev.vaijanath.aiagent.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import org.junit.jupiter.api.Test;

class AsyncAuditSinkTest {

    private static AuditEvent event() {
        return AuditEvent.now("turn.end", "trace", "session", "principal", "tenant", "ok");
    }

    @Test
    void closeFlushesAllEventsToTheDelegate() {
        InMemoryAuditSink delegate = new InMemoryAuditSink();
        try (AsyncAuditSink async = new AsyncAuditSink(delegate)) {
            for (int i = 0; i < 50; i++) {
                async.record(event());
            }
        } // close() drains
        assertEquals(50, delegate.events().size(), "close must flush buffered events");
    }

    @Test
    void recordDoesNotBlockOnASlowSink() {
        AuditSink slow = e -> sleep(20);
        long startNanos = System.nanoTime();
        try (AsyncAuditSink async = new AsyncAuditSink(slow)) {
            for (int i = 0; i < 20; i++) {
                async.record(event()); // would be ~400ms if synchronous
            }
            long enqueueMillis = (System.nanoTime() - startNanos) / 1_000_000;
            assertTrue(enqueueMillis < 200, "enqueue must not block on the slow sink, took " + enqueueMillis + "ms");
        }
    }

    @Test
    void dropsRatherThanBlocksWhenTheBufferIsFull() {
        CountDownLatch block = new CountDownLatch(1);
        AuditSink blocking = e -> {
            try {
                block.await();
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        };
        AsyncAuditSink async = new AsyncAuditSink(blocking, 2);
        try {
            for (int i = 0; i < 20; i++) {
                async.record(event()); // consumer is blocked, so the small buffer fills and the rest drop
            }
            assertTrue(async.droppedCount() > 0, "a full buffer must drop, never block the caller");
        } finally {
            block.countDown();
            async.close();
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
