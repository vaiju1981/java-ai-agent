package dev.vaijanath.aiagent.store.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vaijanath.aiagent.agent.AgentResponse;
import java.nio.file.Path;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JdbcIdempotencyStoreTest {

    private static String url(Path dir) {
        return "jdbc:sqlite:" + dir.resolve("idempotency.db");
    }

    @Test
    void persistsAndReplaysAcrossInstances(@TempDir Path dir) {
        String url = url(dir);
        JdbcIdempotencyStore.fromJdbcUrl(url).save("acme", "k", AgentResponse.completed("answer"));

        // A fresh instance over the same database stands in for a restart.
        AgentResponse found = JdbcIdempotencyStore.fromJdbcUrl(url).lookup("acme", "k").orElseThrow();

        assertEquals("answer", found.output());
        assertEquals("completed", found.stopReason());
    }

    @Test
    void firstWriteWins(@TempDir Path dir) {
        JdbcIdempotencyStore store = JdbcIdempotencyStore.fromJdbcUrl(url(dir));
        store.save("acme", "k", AgentResponse.completed("first"));
        store.save("acme", "k", AgentResponse.completed("second")); // duplicate key → no-op

        assertEquals("first", store.lookup("acme", "k").orElseThrow().output());
    }

    @Test
    void isolatesByTenantAndRoundTripsABlockedResult(@TempDir Path dir) {
        JdbcIdempotencyStore store = JdbcIdempotencyStore.fromJdbcUrl(url(dir));
        store.save("acme", "k", AgentResponse.blocked("safe replacement", "guardrail"));

        assertTrue(store.lookup("other", "k").isEmpty(), "scoped by tenant");
        AgentResponse found = store.lookup("acme", "k").orElseThrow();
        assertTrue(found.blocked());
        assertEquals("guardrail", found.stopReason());
    }

    @Test
    void surfacesConnectionFailuresAsAClearException() {
        JdbcIdempotencyStore store = new JdbcIdempotencyStore(() -> {
            throw new SQLException("database is down");
        });

        assertThrows(IllegalStateException.class, () -> store.lookup("acme", "k"));
        assertThrows(
                IllegalStateException.class, () -> store.save("acme", "k", AgentResponse.completed("x")));
    }
}
