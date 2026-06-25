package dev.vaijanath.aiagent.store.jdbc;

import dev.vaijanath.aiagent.agent.AgentResponse;
import dev.vaijanath.aiagent.agent.IdempotencyStore;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * A durable {@link IdempotencyStore} backed by a JDBC database (SQLite/PostgreSQL): a turn's result is
 * recorded in {@code agent_idempotency} keyed by {@code (tenant, idempotency_key)}, so a retried request
 * returns the prior result across restarts and across instances. First write wins — a duplicate save is a
 * no-op — so concurrent duplicates converge on one stored result (though both may already have executed;
 * pre-execution reservation is out of scope).
 */
public final class JdbcIdempotencyStore implements IdempotencyStore {

    private final ConnectionSource connections;

    public JdbcIdempotencyStore(ConnectionSource connections) {
        this.connections = Objects.requireNonNull(connections, "connections");
    }

    /** A store over a JDBC URL (the driver must be on the classpath), creating the schema if absent. */
    public static JdbcIdempotencyStore fromJdbcUrl(String jdbcUrl) {
        JdbcIdempotencyStore store = new JdbcIdempotencyStore(() -> DriverManager.getConnection(jdbcUrl));
        store.createSchema();
        return store;
    }

    private void createSchema() {
        String ddl = "CREATE TABLE IF NOT EXISTS agent_idempotency ("
                + "tenant VARCHAR(256) NOT NULL,"
                + "idempotency_key VARCHAR(512) NOT NULL,"
                + "output TEXT NOT NULL,"
                + "blocked INTEGER NOT NULL," // 0/1 — portable across SQLite and PostgreSQL
                + "stop_reason VARCHAR(256) NOT NULL,"
                + "created_at BIGINT NOT NULL,"
                + "PRIMARY KEY (tenant, idempotency_key))";
        try (Connection c = connections.get();
                Statement s = c.createStatement()) {
            s.execute(ddl);
        } catch (SQLException e) {
            throw new IllegalStateException("failed to create the agent_idempotency table", e);
        }
    }

    @Override
    public Optional<AgentResponse> lookup(String tenant, String key) {
        String sql = "SELECT output, blocked, stop_reason FROM agent_idempotency "
                + "WHERE tenant = ? AND idempotency_key = ?";
        try (Connection c = connections.get();
                PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, tenant);
            ps.setString(2, key);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(new AgentResponse(
                        rs.getString("output"), rs.getInt("blocked") != 0, rs.getString("stop_reason")));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("failed to look up an idempotency record", e);
        }
    }

    @Override
    public void save(String tenant, String key, AgentResponse response) {
        String sql = "INSERT INTO agent_idempotency "
                + "(tenant, idempotency_key, output, blocked, stop_reason, created_at) "
                + "VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection c = connections.get();
                PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, tenant);
            ps.setString(2, key);
            ps.setString(3, response.output());
            ps.setInt(4, response.blocked() ? 1 : 0);
            ps.setString(5, response.stopReason());
            ps.setLong(6, System.currentTimeMillis());
            ps.executeUpdate();
        } catch (SQLException e) {
            if (isDuplicateKey(e)) {
                return; // first write wins; a concurrent/duplicate save is a no-op
            }
            throw new IllegalStateException("failed to save an idempotency record", e);
        }
    }

    private static boolean isDuplicateKey(SQLException e) {
        String state = e.getSQLState();
        String message = e.getMessage() == null ? "" : e.getMessage().toLowerCase(Locale.ROOT);
        return "23505".equals(state) // PostgreSQL unique_violation
                || e.getErrorCode() == 19 // SQLite SQLITE_CONSTRAINT
                || message.contains("unique") || message.contains("primary key");
    }
}
