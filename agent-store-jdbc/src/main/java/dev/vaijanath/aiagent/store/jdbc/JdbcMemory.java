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
import java.util.UUID;

/**
 * A turn-buffered {@link Memory} for one session. Messages remain local while the agent is running
 * and are committed together with a turn record in one database transaction. A failed or crashed
 * turn therefore never leaves a partial tool-call sequence in durable conversation history.
 */
final class JdbcMemory implements Memory {

    private final ConnectionSource connections;
    private final ObjectMapper mapper;
    private final String tenant;
    private final String sessionId;
    private final List<Message> history;
    private final List<Message> pending = new ArrayList<>();
    private long nextSeq;
    private final String turnId = UUID.randomUUID().toString();

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
        history.add(message);
        pending.add(message);
    }

    void commit() {
        if (pending.isEmpty()) {
            return;
        }
        if (Thread.currentThread().isInterrupted()) {
            throw new IllegalStateException("interrupted turn will not be committed");
        }
        try (Connection c = connections.get()) {
            boolean originalAutoCommit = c.getAutoCommit();
            c.setAutoCommit(false);
            try {
                insertTurn(c);
                long seq = nextSeq;
                for (Message message : pending) {
                    if (Thread.currentThread().isInterrupted()) {
                        throw new InterruptedException("turn interrupted during commit");
                    }
                    persist(c, message, seq++);
                }
                c.commit();
                nextSeq = seq;
                pending.clear();
            } catch (Exception e) {
                c.rollback();
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                if (isConstraintConflict(e)) {
                    throw new ConcurrentConversationException(tenant, sessionId, e);
                }
                throw e;
            } finally {
                c.setAutoCommit(originalAutoCommit);
            }
        } catch (Exception e) {
            if (e instanceof ConcurrentConversationException conflict) {
                throw conflict;
            }
            throw new IllegalStateException(
                    "failed to commit conversation turn for " + tenant + "/" + sessionId, e);
        }
    }

    private static boolean isConstraintConflict(Throwable error) {
        for (Throwable current = error; current != null; current = current.getCause()) {
            if (current instanceof SQLException sql
                    && ("23505".equals(sql.getSQLState()) || sql.getErrorCode() == 19)) {
                return true;
            }
        }
        return false;
    }

    private void insertTurn(Connection c) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO agent_turns(tenant, session_id, turn_id, first_seq, message_count, completed_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?)")) {
            ps.setString(1, tenant);
            ps.setString(2, sessionId);
            ps.setString(3, turnId);
            ps.setLong(4, nextSeq);
            ps.setInt(5, pending.size());
            ps.setLong(6, Instant.now().toEpochMilli());
            ps.executeUpdate();
        }
    }

    private void persist(Connection c, Message message, long seq) throws SQLException {
        String toolCalls = null;
        if (message.hasToolCalls()) {
            try {
                toolCalls = mapper.writeValueAsString(message.toolCalls());
            } catch (Exception e) {
                throw new IllegalStateException("failed to serialize tool calls", e);
            }
        }
        try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO agent_messages(tenant, session_id, seq, role, content, "
                                + "tool_call_id, tool_name, tool_calls, turn_id, created_at) "
                                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            ps.setString(1, tenant);
            ps.setString(2, sessionId);
            ps.setLong(3, seq);
            ps.setString(4, message.role().name());
            ps.setString(5, message.content());
            ps.setString(6, message.toolCallId());
            ps.setString(7, message.toolName());
            ps.setString(8, toolCalls);
            ps.setString(9, turnId);
            ps.setLong(10, Instant.now().toEpochMilli());
            ps.executeUpdate();
        }
    }
}
