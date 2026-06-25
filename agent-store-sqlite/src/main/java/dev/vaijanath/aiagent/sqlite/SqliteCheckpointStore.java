package dev.vaijanath.aiagent.sqlite;

import dev.vaijanath.aiagent.checkpoint.Checkpoint;
import dev.vaijanath.aiagent.checkpoint.CheckpointStore;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A {@link CheckpointStore} backed by embedded SQLite — durable across restarts with zero
 * infrastructure (a single file). A good default durable backend for a single node; for a
 * shared/clustered deployment, back the {@link CheckpointStore} port with a server database instead.
 *
 * <p>Progress is stored normalized across two tables ({@code agent_checkpoint} and
 * {@code agent_checkpoint_step}), so no JSON dependency is needed. Each call opens its own short-lived
 * JDBC connection; SQLite's file locking serializes writers and {@code busy_timeout} avoids spurious
 * "database is locked" errors under light contention.
 */
public final class SqliteCheckpointStore implements CheckpointStore {

    private final String jdbcUrl;

    /** Opens (creating if needed) a SQLite database at the given file path. */
    public SqliteCheckpointStore(Path file) {
        this("jdbc:sqlite:" + Objects.requireNonNull(file, "file"));
    }

    /** Uses the given SQLite JDBC URL, e.g. {@code jdbc:sqlite:/var/lib/agent/checkpoints.db}. */
    public SqliteCheckpointStore(String jdbcUrl) {
        this.jdbcUrl = Objects.requireNonNull(jdbcUrl, "jdbcUrl");
        ensureSchema();
    }

    private Connection open() throws SQLException {
        Connection connection = DriverManager.getConnection(jdbcUrl);
        try (Statement s = connection.createStatement()) {
            s.execute("PRAGMA busy_timeout = 5000");
        }
        return connection;
    }

    private void ensureSchema() {
        try (Connection c = open();
                Statement s = c.createStatement()) {
            s.execute("CREATE TABLE IF NOT EXISTS agent_checkpoint ("
                    + "tenant TEXT NOT NULL, run_id TEXT NOT NULL, task TEXT NOT NULL, "
                    + "PRIMARY KEY (tenant, run_id))");
            s.execute("CREATE TABLE IF NOT EXISTS agent_checkpoint_step ("
                    + "tenant TEXT NOT NULL, run_id TEXT NOT NULL, idx INTEGER NOT NULL, "
                    + "description TEXT NOT NULL, depends_on TEXT NOT NULL, "
                    + "status TEXT NOT NULL, result TEXT NOT NULL, "
                    + "PRIMARY KEY (tenant, run_id, idx))");
        } catch (SQLException e) {
            throw new IllegalStateException("failed to initialize SQLite checkpoint schema", e);
        }
    }

    @Override
    public Optional<Checkpoint> load(String tenant, String runId) {
        try (Connection c = open()) {
            String task = loadTask(c, tenant, runId);
            if (task == null) {
                return Optional.empty();
            }
            return Optional.of(new Checkpoint(task, loadSteps(c, tenant, runId)));
        } catch (SQLException e) {
            throw new IllegalStateException("failed to load checkpoint", e);
        }
    }

    private static String loadTask(Connection c, String tenant, String runId) throws SQLException {
        try (PreparedStatement ps =
                c.prepareStatement("SELECT task FROM agent_checkpoint WHERE tenant = ? AND run_id = ?")) {
            ps.setString(1, tenant);
            ps.setString(2, runId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    private static List<Checkpoint.Step> loadSteps(Connection c, String tenant, String runId)
            throws SQLException {
        List<Checkpoint.Step> steps = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT idx, description, depends_on, status, result FROM agent_checkpoint_step "
                        + "WHERE tenant = ? AND run_id = ? ORDER BY idx")) {
            ps.setString(1, tenant);
            ps.setString(2, runId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    steps.add(new Checkpoint.Step(rs.getInt(1), rs.getString(2),
                            fromCsv(rs.getString(3)), rs.getString(4), rs.getString(5)));
                }
            }
        }
        return steps;
    }

    @Override
    public void save(String tenant, String runId, Checkpoint checkpoint) {
        try (Connection c = open()) {
            c.setAutoCommit(false);
            try {
                deleteRows(c, tenant, runId);
                insertCheckpoint(c, tenant, runId, checkpoint.task());
                insertSteps(c, tenant, runId, checkpoint.steps());
                c.commit();
            } catch (SQLException e) {
                c.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("failed to save checkpoint", e);
        }
    }

    private static void insertCheckpoint(Connection c, String tenant, String runId, String task)
            throws SQLException {
        try (PreparedStatement ps =
                c.prepareStatement("INSERT INTO agent_checkpoint (tenant, run_id, task) VALUES (?, ?, ?)")) {
            ps.setString(1, tenant);
            ps.setString(2, runId);
            ps.setString(3, task);
            ps.executeUpdate();
        }
    }

    private static void insertSteps(
            Connection c, String tenant, String runId, List<Checkpoint.Step> steps) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO agent_checkpoint_step "
                        + "(tenant, run_id, idx, description, depends_on, status, result) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?)")) {
            for (Checkpoint.Step step : steps) {
                ps.setString(1, tenant);
                ps.setString(2, runId);
                ps.setInt(3, step.index());
                ps.setString(4, step.description());
                ps.setString(5, toCsv(step.dependsOn()));
                ps.setString(6, step.status());
                ps.setString(7, step.result());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    @Override
    public void delete(String tenant, String runId) {
        try (Connection c = open()) {
            deleteRows(c, tenant, runId);
        } catch (SQLException e) {
            throw new IllegalStateException("failed to delete checkpoint", e);
        }
    }

    private static void deleteRows(Connection c, String tenant, String runId) throws SQLException {
        executeDelete(c, "DELETE FROM agent_checkpoint_step WHERE tenant = ? AND run_id = ?", tenant, runId);
        executeDelete(c, "DELETE FROM agent_checkpoint WHERE tenant = ? AND run_id = ?", tenant, runId);
    }

    private static void executeDelete(Connection c, String sql, String tenant, String runId)
            throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, tenant);
            ps.setString(2, runId);
            ps.executeUpdate();
        }
    }

    /** Step dependencies are a short list of ints, stored as a comma-separated string. */
    private static String toCsv(List<Integer> values) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(values.get(i));
        }
        return sb.toString();
    }

    private static List<Integer> fromCsv(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        List<Integer> values = new ArrayList<>();
        for (String part : csv.split(",")) {
            values.add(Integer.parseInt(part.trim()));
        }
        return values;
    }
}
