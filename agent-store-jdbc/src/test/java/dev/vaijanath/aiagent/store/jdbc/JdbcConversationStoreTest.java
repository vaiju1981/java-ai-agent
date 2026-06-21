package dev.vaijanath.aiagent.store.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vaijanath.aiagent.model.Message;
import dev.vaijanath.aiagent.model.Role;
import dev.vaijanath.aiagent.model.ToolCall;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
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
        JdbcConversationStore.fromJdbcUrl(url).withMemory("acme", "s", m -> {
            for (int i = 0; i < 5; i++) {
                m.add(Message.user("m" + i));
            }
            return null;
        });

        List<Message> windowed = JdbcConversationStore.fromJdbcUrl(url, 2)
                .withMemory("acme", "s", JdbcConversationStoreTest::historyOf);

        assertEquals(2, windowed.size());
        assertEquals("m3", windowed.get(0).content());
        assertEquals("m4", windowed.get(1).content());
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

    private static List<Message> historyOf(dev.vaijanath.aiagent.memory.Memory memory) {
        return memory.history();
    }
}
