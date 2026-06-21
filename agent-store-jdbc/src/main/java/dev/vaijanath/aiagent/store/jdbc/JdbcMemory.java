package dev.vaijanath.aiagent.store.jdbc;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.vaijanath.aiagent.memory.Memory;
import dev.vaijanath.aiagent.model.Message;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * A write-through {@link Memory} for one session: {@link #history()} is the loaded window plus
 * anything added this turn, and {@link #add} persists each message immediately (so it is queryable
 * and crash-safe as soon as it is recorded). Used only within
 * {@link JdbcConversationStore#withMemory}, which holds the session lock.
 */
final class JdbcMemory implements Memory {

    private final ConnectionSource connections;
    private final ObjectMapper mapper;
    private final String tenant;
    private final String sessionId;
    private final List<Message> history;
    private long nextSeq;

    JdbcMemory(ConnectionSource connections, ObjectMapper mapper, String tenant, String sessionId,
            List<Message> window, long nextSeq) {
        this.connections = connections;
        this.mapper = mapper;
        this.tenant = tenant;
        this.sessionId = sessionId;
        this.history = new ArrayList<>(window);
        this.nextSeq = nextSeq;
    }

    @Override
    public List<Message> history() {
        return List.copyOf(history);
    }

    @Override
    public void add(Message message) {
        persist(message, nextSeq);
        nextSeq++;
        history.add(message);
    }

    private void persist(Message message, long seq) {
        String toolCalls = null;
        if (message.hasToolCalls()) {
            try {
                toolCalls = mapper.writeValueAsString(message.toolCalls());
            } catch (Exception e) {
                throw new IllegalStateException("failed to serialize tool calls", e);
            }
        }
        try (Connection c = connections.get();
                PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO agent_messages(tenant, session_id, seq, role, content, "
                                + "tool_call_id, tool_name, tool_calls, created_at) "
                                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            ps.setString(1, tenant);
            ps.setString(2, sessionId);
            ps.setLong(3, seq);
            ps.setString(4, message.role().name());
            ps.setString(5, message.content());
            ps.setString(6, message.toolCallId());
            ps.setString(7, message.toolName());
            ps.setString(8, toolCalls);
            ps.setLong(9, Instant.now().toEpochMilli());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException(
                    "failed to persist message for " + tenant + "/" + sessionId, e);
        }
    }
}
