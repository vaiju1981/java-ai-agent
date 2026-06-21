package dev.vaijanath.aiagent.store.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.vaijanath.aiagent.model.Message;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

class PostgresConversationStoreTest {

    @Test
    @EnabledIfEnvironmentVariable(named = "POSTGRES_TEST_URL", matches = ".+")
    void concurrentInstancesNeverPersistAPartialOrInterleavedTurn() {
        String url = System.getenv("POSTGRES_TEST_URL");
        String session = "concurrent-" + java.util.UUID.randomUUID();
        JdbcConversationStore first = JdbcConversationStore.fromJdbcUrl(url);
        JdbcConversationStore second = JdbcConversationStore.fromJdbcUrl(url);
        CyclicBarrier bothLoaded = new CyclicBarrier(2);
        AtomicInteger succeeded = new AtomicInteger();
        AtomicInteger failed = new AtomicInteger();

        CompletableFuture<Void> a = write(first, session, "a", bothLoaded, succeeded, failed);
        CompletableFuture<Void> b = write(second, session, "b", bothLoaded, succeeded, failed);
        CompletableFuture.allOf(a, b).join();

        List<Message> durable = JdbcConversationStore.fromJdbcUrl(url)
                .withMemory("integration", session, memory -> memory.history());
        assertEquals(1, succeeded.get());
        assertEquals(1, failed.get());
        assertEquals(2, durable.size(), "one complete user/assistant turn must win");
    }

    private static CompletableFuture<Void> write(
            JdbcConversationStore store,
            String session,
            String value,
            CyclicBarrier bothLoaded,
            AtomicInteger succeeded,
            AtomicInteger failed) {
        return CompletableFuture.runAsync(() -> {
            try {
                store.withMemory("integration", session, memory -> {
                    await(bothLoaded);
                    memory.add(Message.user(value));
                    memory.add(Message.assistant("answer-" + value));
                    return null;
                });
                succeeded.incrementAndGet();
            } catch (ConcurrentConversationException expected) {
                failed.incrementAndGet();
            }
        });
    }

    private static void await(CyclicBarrier barrier) {
        try {
            barrier.await();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
