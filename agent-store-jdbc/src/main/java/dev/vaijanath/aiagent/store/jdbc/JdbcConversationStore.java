package dev.vaijanath.aiagent.store.jdbc;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.vaijanath.aiagent.memory.ConversationStore;
import dev.vaijanath.aiagent.memory.Memory;
import dev.vaijanath.aiagent.model.Message;
import dev.vaijanath.aiagent.model.Role;
import dev.vaijanath.aiagent.model.ToolCall;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * A durable {@link ConversationStore} backed by a SQL database (SQLite, PostgreSQL, MySQL, …). Each
 * message is a row in a queryable {@code agent_messages} table — tenant, session, sequence, role,
 * content, tool-call data, and a timestamp — so conversations survive restarts and can be analysed
 * with plain SQL (unlike an in-memory or opaque file store). Wire it into an agent with
 * {@code DefaultAgent.builder().conversationStore(store)}.
 *
 * <p>Set a {@code maxMessages} window to replay only the most recent N messages to the model while
 * still persisting the full history for analytics.
 *
 * <p>Concurrency: work on a single {@code (tenant, session)} is serialized in-process (striped
 * locks), correct for a single service instance. If multiple instances may write the same session
 * concurrently, use sticky routing or external coordination — a clash surfaces as a primary-key
 * conflict (a loud failure), never silent corruption.
 */
public final class JdbcConversationStore implements ConversationStore {

    private static final int STRIPES = 64;
    private static final TypeReference<List<ToolCall>> TOOL_CALL_LIST = new TypeReference<>() {};

    private final ConnectionSource connections;
    private final ObjectMapper mapper = new ObjectMapper();
    private final int maxMessages; // 0 = replay the whole history
    private final Object[] locks = new Object[STRIPES];

    public JdbcConversationStore(ConnectionSource connections) {
        this(connections, 0);
    }

    public JdbcConversationStore(ConnectionSource connections, int maxMessages) {
        this.connections = Objects.requireNonNull(connections, "connections");
        this.maxMessages = Math.max(0, maxMessages);
        for (int i = 0; i < STRIPES; i++) {
            locks[i] = new Object();
        }
        createSchema();
    }

    /** A store over a JDBC URL (the driver must be on the classpath), e.g. {@code jdbc:sqlite:app.db}. */
    public static JdbcConversationStore fromJdbcUrl(String jdbcUrl) {
        return new JdbcConversationStore(() -> DriverManager.getConnection(jdbcUrl));
    }

    /** A store over a JDBC URL that replays only the most recent {@code maxMessages} per session. */
    public static JdbcConversationStore fromJdbcUrl(String jdbcUrl, int maxMessages) {
        return new JdbcConversationStore(() -> DriverManager.getConnection(jdbcUrl), maxMessages);
    }

    @Override
    public <R> R withMemory(String tenant, String sessionId, Function<Memory, R> action) {
        // Serialize same-session work in-process; different sessions (different stripes) run freely.
        synchronized (locks[Math.floorMod(Objects.hash(tenant, sessionId), STRIPES)]) {
            return action.apply(load(tenant, sessionId));
        }
    }

    private JdbcMemory load(String tenant, String sessionId) {
        List<Message> window = new ArrayList<>();
        long nextSeq;
        try (Connection c = connections.get()) {
            nextSeq = nextSeq(c, tenant, sessionId);
            String sql = "SELECT seq, role, content, tool_call_id, tool_name, tool_calls "
                    + "FROM agent_messages WHERE tenant = ? AND session_id = ? ORDER BY seq"
                    + (maxMessages > 0 ? " DESC LIMIT " + maxMessages : " ASC");
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setString(1, tenant);
                ps.setString(2, sessionId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        window.add(toMessage(rs));
                    }
                }
            }
            if (maxMessages > 0) {
                Collections.reverse(window); // DESC LIMIT returned newest-first; restore chronological
            }
        } catch (SQLException e) {
            throw new IllegalStateException(
                    "failed to load conversation " + tenant + "/" + sessionId, e);
        }
        return new JdbcMemory(connections, mapper, tenant, sessionId, window, nextSeq);
    }

    private static long nextSeq(Connection c, String tenant, String sessionId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT COALESCE(MAX(seq), -1) + 1 FROM agent_messages WHERE tenant = ? AND session_id = ?")) {
            ps.setString(1, tenant);
            ps.setString(2, sessionId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    private Message toMessage(ResultSet rs) throws SQLException {
        Role role = Role.valueOf(rs.getString("role"));
        String toolCallsJson = rs.getString("tool_calls");
        List<ToolCall> toolCalls = List.of();
        if (toolCallsJson != null && !toolCallsJson.isBlank()) {
            try {
                toolCalls = mapper.readValue(toolCallsJson, TOOL_CALL_LIST);
            } catch (Exception e) {
                throw new SQLException("corrupt tool_calls for a stored message", e);
            }
        }
        return new Message(
                role, rs.getString("content"), toolCalls,
                rs.getString("tool_call_id"), rs.getString("tool_name"));
    }

    private void createSchema() {
        String ddl = "CREATE TABLE IF NOT EXISTS agent_messages ("
                + "tenant VARCHAR(255) NOT NULL,"
                + "session_id VARCHAR(255) NOT NULL,"
                + "seq BIGINT NOT NULL,"
                + "role VARCHAR(32) NOT NULL,"
                + "content TEXT,"
                + "tool_call_id VARCHAR(255),"
                + "tool_name VARCHAR(255),"
                + "tool_calls TEXT,"
                + "created_at BIGINT NOT NULL,"
                + "PRIMARY KEY (tenant, session_id, seq))";
        try (Connection c = connections.get(); Statement s = c.createStatement()) {
            s.execute(ddl);
            s.execute("CREATE INDEX IF NOT EXISTS idx_agent_messages_created "
                    + "ON agent_messages(created_at)");
        } catch (SQLException e) {
            throw new IllegalStateException("failed to initialize agent_messages schema", e);
        }
    }
}
