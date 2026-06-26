package dev.vaijanath.aiagent.store.jdbc;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.vaijanath.aiagent.memory.Episode;
import dev.vaijanath.aiagent.memory.EpisodicStore;
import dev.vaijanath.aiagent.rag.Embedder;
import dev.vaijanath.aiagent.rag.Vectors;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * A durable, <b>semantic</b> {@link EpisodicStore} backed by SQLite or PostgreSQL: each {@link Episode}
 * is a row in a queryable {@code agent_episodes} table, with its task+lesson embedded for recall. Unlike
 * the in-process keyword stores, recall is by embedding cosine similarity — so a lesson surfaces even
 * when a new task is worded differently — and it <b>survives restarts and is shared across instances</b>,
 * which is what makes a {@code ReflectiveAgent}'s learning persist in production rather than per-process.
 *
 * <p>Embeddings are stored as JSON and ranked in-application (portable across databases, no extension);
 * recall scans a tenant's episodes, which is correct and fast for small-to-moderate histories. For very
 * large histories, front the same {@link EpisodicStore} seam with a pgvector index.
 */
public final class JdbcEpisodicStore implements EpisodicStore {

    private final ConnectionSource connections;
    private final Embedder embedder;
    private final ObjectMapper mapper = new ObjectMapper();

    public JdbcEpisodicStore(ConnectionSource connections, Embedder embedder) {
        this.connections = Objects.requireNonNull(connections, "connections");
        this.embedder = Objects.requireNonNull(embedder, "embedder");
    }

    /**
     * A local convenience over a {@code DriverManager} URL that also creates the schema — the same
     * initialization a migration tool (e.g. Flyway) would own in a service.
     */
    public static JdbcEpisodicStore fromJdbcUrl(String url, Embedder embedder) {
        JdbcEpisodicStore store = new JdbcEpisodicStore(() -> DriverManager.getConnection(url), embedder);
        store.ensureSchema();
        return store;
    }

    /** Creates {@code agent_episodes} (and its tenant index) if absent. Portable DDL (SQLite + PostgreSQL). */
    public void ensureSchema() {
        try (Connection c = connections.get();
                Statement st = c.createStatement()) {
            st.execute("CREATE TABLE IF NOT EXISTS agent_episodes ("
                    + "id VARCHAR(64) NOT NULL,"
                    + "tenant VARCHAR(256) NOT NULL,"
                    + "task TEXT NOT NULL,"
                    + "outcome TEXT NOT NULL,"
                    + "success INTEGER NOT NULL,"
                    + "lesson TEXT NOT NULL,"
                    + "embedding TEXT NOT NULL,"
                    + "created_at BIGINT NOT NULL,"
                    + "PRIMARY KEY (id))");
            st.execute("CREATE INDEX IF NOT EXISTS idx_agent_episodes_tenant ON agent_episodes(tenant)");
        } catch (SQLException e) {
            throw new IllegalStateException("failed to create agent_episodes schema", e);
        }
    }

    @Override
    public void record(Episode episode) {
        Objects.requireNonNull(episode, "episode");
        String embedding = write(embedder.embed(recallText(episode.task(), episode.lesson())));
        try (Connection c = connections.get();
                PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO agent_episodes "
                                + "(id, tenant, task, outcome, success, lesson, embedding, created_at) "
                                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {
            ps.setString(1, UUID.randomUUID().toString());
            ps.setString(2, episode.tenant());
            ps.setString(3, episode.task());
            ps.setString(4, episode.outcome());
            ps.setInt(5, episode.success() ? 1 : 0);
            ps.setString(6, episode.lesson());
            ps.setString(7, embedding);
            ps.setLong(8, System.currentTimeMillis());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("failed to record episode", e);
        }
    }

    @Override
    public List<Episode> recall(String tenant, String query, int limit) {
        if (limit <= 0 || query == null || query.isBlank()) {
            return List.of();
        }
        float[] q = embedder.embed(query);
        List<Scored> scored = new ArrayList<>();
        try (Connection c = connections.get();
                PreparedStatement ps = c.prepareStatement("SELECT task, outcome, success, lesson, embedding "
                        + "FROM agent_episodes WHERE tenant = ?")) {
            ps.setString(1, tenant);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    double score = Vectors.cosine(q, read(rs.getString("embedding")));
                    if (score > 0.0) {
                        Episode e = new Episode(tenant, rs.getString("task"), rs.getString("outcome"),
                                rs.getInt("success") != 0, rs.getString("lesson"));
                        scored.add(new Scored(e, score));
                    }
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("failed to recall episodes for tenant '" + tenant + "'", e);
        }
        scored.sort(Comparator.comparingDouble(Scored::score).reversed());
        return scored.stream().limit(limit).map(Scored::episode).toList();
    }

    private static String recallText(String task, String lesson) {
        return (task == null ? "" : task) + " " + (lesson == null ? "" : lesson);
    }

    private record Scored(Episode episode, double score) {}

    private String write(float[] vector) {
        try {
            return mapper.writeValueAsString(vector);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("failed to serialize embedding", e);
        }
    }

    private float[] read(String json) {
        try {
            return mapper.readValue(json, float[].class);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("corrupt stored embedding", e);
        }
    }
}
