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
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * A durable {@link ConversationStore} backed by SQLite or PostgreSQL. Each
 * message is a row in a queryable {@code agent_messages} table — tenant, session, sequence, role,
 * content, tool-call data, and a timestamp — so conversations survive restarts and can be analysed
 * with plain SQL (unlike an in-memory or opaque file store). Wire it into an agent with
 * {@code DefaultAgent.builder().conversationStore(store)}.
 *
 * <p>Set a {@code maxTurns} window to replay only the most recent N complete turns plus all system
 * messages while still persisting the full history for analytics.
 *
 * <p>Concurrency: work on a single {@code (tenant, session)} is serialized in-process (striped
 * locks), correct for a single service instance. If multiple instances may write the same session
 * concurrently, use sticky routing or external coordination — a clash rolls back the whole turn and
 * surfaces as a primary-key conflict, never a partially persisted conversation. For idempotent or
 * replayable turns, {@link #withConflictRetries} re-runs the turn against fresh history a bounded
 * number of times before surfacing the conflict.
 */
public final class JdbcConversationStore implements ConversationStore {

    private static final int STRIPES = 64;
    private static final long DEFAULT_CONFLICT_BACKOFF_MILLIS = 20;
    private static final TypeReference<List<ToolCall>> TOOL_CALL_LIST = new TypeReference<>() {};

    private final ConnectionSource connections;
    private final ObjectMapper mapper = new ObjectMapper();
    private final int maxTurns; // 0 = replay the whole history
    private final int maxConflictRetries; // 0 = surface the conflict immediately
    private final long conflictBackoffMillis;
    private final Object[] locks = new Object[STRIPES];

    public JdbcConversationStore(ConnectionSource connections) {
        this(connections, 0, false);
    }

    public JdbcConversationStore(ConnectionSource connections, int maxTurns) {
        this(connections, maxTurns, false);
    }

    private JdbcConversationStore(ConnectionSource connections, int maxTurns, boolean initializeSchema) {
        this(connections, maxTurns, initializeSchema, 0, DEFAULT_CONFLICT_BACKOFF_MILLIS);
    }

    private JdbcConversationStore(ConnectionSource connections, int maxTurns, boolean initializeSchema,
            int maxConflictRetries, long conflictBackoffMillis) {
        this.connections = Objects.requireNonNull(connections, "connections");
        this.maxTurns = Math.max(0, maxTurns);
        this.maxConflictRetries = Math.max(0, maxConflictRetries);
        this.conflictBackoffMillis = Math.max(0, conflictBackoffMillis);
        for (int i = 0; i < STRIPES; i++) {
            locks[i] = new Object();
        }
        if (initializeSchema) {
            createSchema();
        }
    }

    /**
     * Returns a copy that, when a concurrent writer wins the commit race, reloads the session and
     * re-runs the turn up to {@code maxRetries} times (with {@code backoff} between attempts) before
     * surfacing a {@link ConcurrentConversationException}. The turn's action is re-executed, so use
     * this only when repeating it is safe — idempotent/allow-listed tools or a deterministic replay.
     * Otherwise leave it off (the default) and retry at a layer that can rebuild the request.
     */
    public JdbcConversationStore withConflictRetries(int maxRetries, java.time.Duration backoff) {
        Objects.requireNonNull(backoff, "backoff");
        return new JdbcConversationStore(
                connections, maxTurns, false, maxRetries, backoff.toMillis());
    }

    /** A store over a JDBC URL (the driver must be on the classpath), e.g. {@code jdbc:sqlite:app.db}. */
    public static JdbcConversationStore fromJdbcUrl(String jdbcUrl) {
        return new JdbcConversationStore(() -> DriverManager.getConnection(jdbcUrl), 0, true);
    }

    /** A store over a JDBC URL that replays only the most recent {@code maxTurns} complete turns. */
    public static JdbcConversationStore fromJdbcUrl(String jdbcUrl, int maxTurns) {
        return new JdbcConversationStore(
                () -> DriverManager.getConnection(jdbcUrl), maxTurns, true);
    }

    @Override
    public <R> R withMemory(String tenant, String sessionId, Function<Memory, R> action) {
        // Serialize same-session work in-process; different sessions (different stripes) run freely.
        synchronized (locks[Math.floorMod(Objects.hash(tenant, sessionId), STRIPES)]) {
            int attempt = 0;
            while (true) {
                JdbcMemory memory = load(tenant, sessionId);
                try {
                    R result = action.apply(memory);
                    memory.commit();
                    return result;
                } catch (ConcurrentConversationException conflict) {
                    if (attempt++ >= maxConflictRetries) {
                        throw conflict;
                    }
                    backoffBeforeRetry();
                    // Loop: reload fresh history (now including the winner's turn) and re-run.
                }
            }
        }
    }

    private void backoffBeforeRetry() {
        if (conflictBackoffMillis <= 0) {
            return;
        }
        try {
            Thread.sleep(conflictBackoffMillis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while retrying a conflicted turn", e);
        }
    }

    // Package-private so a test can drive the cross-instance commit race deterministically.
    JdbcMemory load(String tenant, String sessionId) {
        List<Message> window = new ArrayList<>();
        long nextSeq;
        try (Connection c = connections.get()) {
            nextSeq = nextSeq(c, tenant, sessionId);
            String sql = "SELECT m.seq, m.role, m.content, m.tool_call_id, m.tool_name, m.tool_calls "
                    + "FROM agent_messages m JOIN agent_turns t "
                    + "ON t.tenant=m.tenant AND t.session_id=m.session_id AND t.turn_id=m.turn_id "
                    + "WHERE m.tenant = ? AND m.session_id = ? "
                    + (maxTurns > 0
                            ? "AND (m.role='SYSTEM' OR t.turn_id IN (SELECT turn_id FROM agent_turns "
                                    + "WHERE tenant=? AND session_id=? ORDER BY first_seq DESC LIMIT " + maxTurns
                                    + ")) "
                            : "")
                    + "ORDER BY m.seq";
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setString(1, tenant);
                ps.setString(2, sessionId);
                if (maxTurns > 0) {
                    ps.setString(3, tenant);
                    ps.setString(4, sessionId);
                }
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        window.add(toMessage(rs));
                    }
                }
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
                + "turn_id VARCHAR(255) NOT NULL,"
                + "created_at BIGINT NOT NULL,"
                + "PRIMARY KEY (tenant, session_id, seq),"
                + "FOREIGN KEY (tenant, session_id, turn_id) "
                + "REFERENCES agent_turns(tenant, session_id, turn_id))";
        String turnsDdl = "CREATE TABLE IF NOT EXISTS agent_turns ("
                + "tenant VARCHAR(255) NOT NULL,"
                + "session_id VARCHAR(255) NOT NULL,"
                + "turn_id VARCHAR(255) NOT NULL,"
                + "first_seq BIGINT NOT NULL,"
                + "message_count INTEGER NOT NULL,"
                + "completed_at BIGINT NOT NULL,"
                + "PRIMARY KEY (tenant, session_id, turn_id),"
                + "UNIQUE (tenant, session_id, first_seq))";
        try (Connection c = connections.get(); Statement s = c.createStatement()) {
            s.execute(turnsDdl);
            s.execute(ddl);
            s.execute("CREATE INDEX IF NOT EXISTS idx_agent_messages_created "
                    + "ON agent_messages(created_at)");
        } catch (SQLException e) {
            throw new IllegalStateException("failed to initialize agent_messages schema", e);
        }
    }
}
