package dev.vaijanath.aiagent.store.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vaijanath.aiagent.memory.ConversationHistory;
import dev.vaijanath.aiagent.model.Message;
import dev.vaijanath.aiagent.model.Role;
import dev.vaijanath.aiagent.model.ToolCall;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JdbcConversationStoreTest {

    private static String url(Path dir) {
        return "jdbc:sqlite:" + dir.resolve("conversations.db");
    }

    @Test
    void persistsAcrossInstances(@TempDir Path dir) {
        String url = url(dir);
        JdbcConversationStore.fromJdbcUrl(url).withMemory("acme", "s1", m -> {
            m.add(Message.system("be helpful"));
            m.add(Message.user("hi"));
            m.add(Message.assistant("hello"));
            return null;
        });

        // A fresh instance over the same DB stands in for a restart.
        List<Message> history = JdbcConversationStore.fromJdbcUrl(url)
                .withMemory("acme", "s1", JdbcConversationStoreTest::historyOf);

        assertEquals(3, history.size());
        assertEquals(Role.USER, history.get(1).role());
        assertEquals("hi", history.get(1).content());
    }

    @Test
    void isolatesTenantsReusingTheSameSessionId(@TempDir Path dir) {
        JdbcConversationStore store = JdbcConversationStore.fromJdbcUrl(url(dir));
        store.withMemory("tenant-a", "shared", m -> {
            m.add(Message.user("A"));
            return null;
        });
        store.withMemory("tenant-b", "shared", m -> {
            m.add(Message.user("B"));
            return null;
        });

        String tenantA = store.withMemory("tenant-a", "shared", m -> m.history().get(0).content());
        int tenantBCount = store.withMemory("tenant-b", "shared", m -> m.history().size());
        assertEquals("A", tenantA);
        assertEquals(1, tenantBCount);
    }

    @Test
    void roundTripsToolCalls(@TempDir Path dir) {
        String url = url(dir);
        JdbcConversationStore.fromJdbcUrl(url).withMemory("acme", "s", m -> {
            m.add(Message.assistant("", List.of(new ToolCall("c1", "search", "{\"q\":\"java\"}"))));
            m.add(Message.toolResult("c1", "search", "results"));
            return null;
        });

        List<Message> history = JdbcConversationStore.fromJdbcUrl(url)
                .withMemory("acme", "s", JdbcConversationStoreTest::historyOf);

        Message assistant = history.get(0);
        assertTrue(assistant.hasToolCalls(), "assistant tool calls must survive a round trip");
        assertEquals("search", assistant.toolCalls().get(0).name());
        assertEquals("{\"q\":\"java\"}", assistant.toolCalls().get(0).argumentsJson());

        Message toolResult = history.get(1);
        assertEquals(Role.TOOL, toolResult.role());
        assertEquals("c1", toolResult.toolCallId());
        assertEquals("results", toolResult.content());
    }

    @Test
    void windowReplaysOnlyTheMostRecentMessages(@TempDir Path dir) {
        String url = url(dir);
        JdbcConversationStore store = JdbcConversationStore.fromJdbcUrl(url);
        for (int i = 0; i < 5; i++) {
            int turn = i;
            store.withMemory("acme", "s", m -> {
                m.add(Message.user("m" + turn));
                return null;
            });
        }

        List<Message> windowed = JdbcConversationStore.fromJdbcUrl(url, 2)
                .withMemory("acme", "s", JdbcConversationStoreTest::historyOf);

        assertEquals(2, windowed.size());
        assertEquals("m3", windowed.get(0).content());
        assertEquals("m4", windowed.get(1).content());
    }

    @Test
    void failedTurnLeavesNoPartialMessages(@TempDir Path dir) {
        String url = url(dir);
        JdbcConversationStore store = JdbcConversationStore.fromJdbcUrl(url);

        assertThrows(IllegalStateException.class, () -> store.withMemory("acme", "s", m -> {
            m.add(Message.user("must roll back"));
            throw new IllegalStateException("boom");
        }));

        List<Message> history = JdbcConversationStore.fromJdbcUrl(url)
                .withMemory("acme", "s", JdbcConversationStoreTest::historyOf);
        assertTrue(history.isEmpty(), "a failed turn must not leave partial durable history");
    }

    @Test
    void interruptedTurnDoesNotCommitLate(@TempDir Path dir) {
        String url = url(dir);
        JdbcConversationStore store = JdbcConversationStore.fromJdbcUrl(url);
        try {
            assertThrows(IllegalStateException.class, () -> store.withMemory("acme", "s", m -> {
                m.add(Message.user("late write"));
                Thread.currentThread().interrupt();
                return null;
            }));
        } finally {
            Thread.interrupted(); // clear the test thread's intentional interrupt
        }

        List<Message> history = JdbcConversationStore.fromJdbcUrl(url)
                .withMemory("acme", "s", JdbcConversationStoreTest::historyOf);
        assertTrue(history.isEmpty());
    }

    @Test
    void windowKeepsSystemPromptAndCompleteTurns(@TempDir Path dir) {
        String url = url(dir);
        JdbcConversationStore store = JdbcConversationStore.fromJdbcUrl(url);
        store.withMemory("acme", "s", m -> {
            m.add(Message.system("system"));
            m.add(Message.user("old"));
            m.add(Message.assistant("old answer"));
            return null;
        });
        store.withMemory("acme", "s", m -> {
            m.add(Message.user("new"));
            m.add(Message.assistant("new answer"));
            return null;
        });

        List<Message> history = JdbcConversationStore.fromJdbcUrl(url, 1)
                .withMemory("acme", "s", JdbcConversationStoreTest::historyOf);
        assertEquals(List.of(Role.SYSTEM, Role.USER, Role.ASSISTANT),
                history.stream().map(Message::role).toList());
        assertEquals("system", history.get(0).content());
        assertEquals("new", history.get(1).content());
    }

    @Test
    void writesAnalyticsFriendlyRows(@TempDir Path dir) throws Exception {
        String url = url(dir);
        JdbcConversationStore.fromJdbcUrl(url).withMemory("acme", "s", m -> {
            m.add(Message.user("hi"));
            m.add(Message.assistant("hello"));
            return null;
        });

        // The data is queryable with plain SQL — the point of a relational store.
        try (Connection c = DriverManager.getConnection(url);
                Statement st = c.createStatement();
                ResultSet rs = st.executeQuery(
                        "SELECT role, COUNT(*) AS n FROM agent_messages "
                                + "WHERE tenant = 'acme' GROUP BY role ORDER BY role")) {
            rs.next();
            assertEquals("ASSISTANT", rs.getString("role"));
            assertEquals(1, rs.getInt("n"));
            rs.next();
            assertEquals("USER", rs.getString("role"));
            assertEquals(1, rs.getInt("n"));
        }
    }

    @Test
    void pooledConstructorRequiresMigrationOwnedSchema(@TempDir Path dir) throws Exception {
        String url = url(dir);
        JdbcConversationStore managed = new JdbcConversationStore(() -> DriverManager.getConnection(url));

        assertThrows(IllegalStateException.class,
                () -> managed.withMemory("acme", "s", JdbcConversationStoreTest::historyOf));

        // The local convenience performs the same initialization that Flyway owns in a service.
        JdbcConversationStore.fromJdbcUrl(url);
        assertTrue(managed.withMemory("acme", "s", JdbcConversationStoreTest::historyOf).isEmpty());
    }

    @Test
    void handlesParallelIndependentSessions(@TempDir Path dir) {
        JdbcConversationStore store = JdbcConversationStore.fromJdbcUrl(url(dir));
        List<CompletableFuture<Void>> writes = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            int session = i;
            writes.add(CompletableFuture.runAsync(() -> store.withMemory("acme", "s" + session, m -> {
                m.add(Message.user("message-" + session));
                return null;
            })));
        }
        CompletableFuture.allOf(writes.toArray(CompletableFuture[]::new)).join();

        for (int i = 0; i < 40; i++) {
            List<Message> history = store.withMemory(
                    "acme", "s" + i, JdbcConversationStoreTest::historyOf);
            assertEquals(List.of("message-" + i), history.stream().map(Message::content).toList());
        }
    }

    @Test
    void losingTheCommitRaceSurfacesAsAConflictAndRollsBack(@TempDir Path dir) {
        String url = url(dir);
        JdbcConversationStore a = JdbcConversationStore.fromJdbcUrl(url);
        JdbcConversationStore b = JdbcConversationStore.fromJdbcUrl(url); // a second service instance

        // Both load the same session position before either commits — the cross-instance race.
        JdbcMemory mA = a.load("acme", "s");
        JdbcMemory mB = b.load("acme", "s");
        mA.add(Message.user("from A"));
        mB.add(Message.user("from B"));

        mA.commit(); // wins the race
        assertThrows(ConcurrentConversationException.class, mB::commit); // loses, rolls back whole turn

        // The winner's turn is the only durable one; the loser left nothing partial behind.
        List<Message> history = JdbcConversationStore.fromJdbcUrl(url)
                .withMemory("acme", "s", JdbcConversationStoreTest::historyOf);
        assertEquals(List.of("from A"), history.stream().map(Message::content).toList());
    }

    @Test
    void conflictRetryReloadsFreshHistoryAndCompletesTheTurn(@TempDir Path dir) {
        String url = url(dir);
        JdbcConversationStore interloper = JdbcConversationStore.fromJdbcUrl(url);
        JdbcConversationStore store = JdbcConversationStore.fromJdbcUrl(url)
                .withConflictRetries(3, Duration.ofMillis(5));

        AtomicInteger attempts = new AtomicInteger();
        store.withMemory("acme", "s", m -> {
            if (attempts.getAndIncrement() == 0) {
                // A different instance commits at our position during our first attempt.
                interloper.withMemory("acme", "s", other -> {
                    other.add(Message.user("interloper"));
                    return null;
                });
            }
            m.add(Message.user("mine"));
            return null;
        });

        assertEquals(2, attempts.get(), "first attempt conflicts; the retry reloads and succeeds");
        List<Message> history = JdbcConversationStore.fromJdbcUrl(url)
                .withMemory("acme", "s", JdbcConversationStoreTest::historyOf);
        assertEquals(List.of("interloper", "mine"), history.stream().map(Message::content).toList());
    }

    @Test
    void browsesHistoryListingSessionsAndReadingMessages(@TempDir Path dir) {
        JdbcConversationStore store = JdbcConversationStore.fromJdbcUrl(url(dir));
        store.withMemory("acme", "s1", m -> {
            m.add(Message.user("hi"));
            m.add(Message.assistant("hello"));
            return null;
        });
        store.withMemory("acme", "s2", m -> {
            m.add(Message.user("again"));
            return null;
        });
        store.withMemory("other", "s3", m -> {
            m.add(Message.user("nope"));
            return null;
        });

        List<ConversationHistory.SessionSummary> sessions = store.listSessions("acme");
        assertEquals(2, sessions.size(), "only the tenant's own sessions are listed");
        assertTrue(sessions.stream().anyMatch(s -> s.sessionId().equals("s1") && s.messageCount() == 2));
        assertTrue(sessions.stream().anyMatch(s -> s.sessionId().equals("s2") && s.messageCount() == 1));

        List<Message> s1 = store.messages("acme", "s1");
        assertEquals(List.of("hi", "hello"), s1.stream().map(Message::content).toList());
        assertTrue(store.messages("acme", "missing").isEmpty(), "an unknown session reads empty");
        assertTrue(store.messages("other", "s1").isEmpty(), "another tenant cannot read the session");
    }

    @Test
    void deleteRemovesASessionsHistory(@TempDir Path dir) {
        JdbcConversationStore store = JdbcConversationStore.fromJdbcUrl(url(dir));
        store.withMemory("acme", "s1", m -> {
            m.add(Message.user("hi"));
            m.add(Message.assistant("hello"));
            return null;
        });
        store.withMemory("acme", "s2", m -> {
            m.add(Message.user("keep me"));
            return null;
        });

        store.delete("acme", "s1");

        assertTrue(store.messages("acme", "s1").isEmpty(), "the deleted session has no messages");
        assertEquals(1, store.listSessions("acme").size(), "only the deleted session is gone");
        assertEquals(List.of("keep me"), store.messages("acme", "s2").stream().map(Message::content).toList());
    }

    private static List<Message> historyOf(dev.vaijanath.aiagent.memory.Memory memory) {
        return memory.history();
    }
}
