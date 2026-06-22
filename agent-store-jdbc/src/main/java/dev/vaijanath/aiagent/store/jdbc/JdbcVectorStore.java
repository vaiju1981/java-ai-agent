package dev.vaijanath.aiagent.store.jdbc;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.vaijanath.aiagent.rag.Embedder;
import dev.vaijanath.aiagent.rag.RetrievedChunk;
import dev.vaijanath.aiagent.rag.Retriever;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A durable {@link Retriever} backed by SQLite or PostgreSQL: each chunk is a row in a queryable
 * {@code rag_chunks} table (tenant, id, content, metadata, embedding), so a corpus survives restarts
 * and is inspectable with plain SQL. Embeddings are stored as JSON and ranked by cosine similarity
 * in-application, which is portable across databases and needs no extension.
 *
 * <p>Scope note: retrieval scans a tenant's rows and ranks them in memory — correct and fast for
 * small-to-moderate corpora. For large-scale approximate nearest-neighbour search, front the same
 * {@link Retriever} seam with a pgvector index or a dedicated vector store; this implementation is the
 * dependency-light default.
 */
public final class JdbcVectorStore implements Retriever {

    private static final TypeReference<Map<String, String>> METADATA = new TypeReference<>() {};

    private final ConnectionSource connections;
    private final Embedder embedder;
    private final ObjectMapper mapper = new ObjectMapper();

    public JdbcVectorStore(ConnectionSource connections, Embedder embedder) {
        this.connections = Objects.requireNonNull(connections, "connections");
        this.embedder = Objects.requireNonNull(embedder, "embedder");
    }

    /**
     * A local convenience over a {@code DriverManager} URL that also creates the schema — the same
     * initialization a migration tool (e.g. Flyway) would own in a service.
     */
    public static JdbcVectorStore fromJdbcUrl(String url, Embedder embedder) {
        JdbcVectorStore store = new JdbcVectorStore(() -> DriverManager.getConnection(url), embedder);
        store.ensureSchema();
        return store;
    }

    /** Creates {@code rag_chunks} if absent. Portable DDL (SQLite + PostgreSQL). */
    public void ensureSchema() {
        try (Connection c = connections.get();
                Statement st = c.createStatement()) {
            st.execute("CREATE TABLE IF NOT EXISTS rag_chunks ("
                    + "tenant VARCHAR(128) NOT NULL,"
                    + "chunk_id VARCHAR(256) NOT NULL,"
                    + "content TEXT NOT NULL,"
                    + "metadata TEXT NOT NULL,"
                    + "embedding TEXT NOT NULL,"
                    + "PRIMARY KEY (tenant, chunk_id))");
        } catch (SQLException e) {
            throw new IllegalStateException("failed to create rag_chunks schema", e);
        }
    }

    /** Embeds and upserts a chunk under {@code tenant}. */
    public void add(String tenant, String id, String text, Map<String, String> metadata) {
        Objects.requireNonNull(tenant, "tenant");
        Objects.requireNonNull(id, "id");
        String embedding = write(embedder.embed(text));
        String meta = write(metadata == null ? Map.of() : metadata);
        try (Connection c = connections.get();
                PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO rag_chunks (tenant, chunk_id, content, metadata, embedding) "
                                + "VALUES (?, ?, ?, ?, ?) "
                                + "ON CONFLICT (tenant, chunk_id) DO UPDATE SET "
                                + "content = excluded.content, metadata = excluded.metadata, "
                                + "embedding = excluded.embedding")) {
            ps.setString(1, tenant);
            ps.setString(2, id);
            ps.setString(3, text == null ? "" : text);
            ps.setString(4, meta);
            ps.setString(5, embedding);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("failed to store chunk '" + id + "'", e);
        }
    }

    public void add(String tenant, String id, String text) {
        add(tenant, id, text, Map.of());
    }

    @Override
    public List<RetrievedChunk> retrieve(String tenant, String query, int limit) {
        if (limit <= 0) {
            return List.of();
        }
        float[] q = embedder.embed(query);
        List<RetrievedChunk> scored = new ArrayList<>();
        try (Connection c = connections.get();
                PreparedStatement ps = c.prepareStatement(
                        "SELECT chunk_id, content, metadata, embedding FROM rag_chunks WHERE tenant = ?")) {
            ps.setString(1, tenant);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    double score = cosine(q, read(rs.getString("embedding"), float[].class));
                    if (score > 0.0) {
                        scored.add(new RetrievedChunk(
                                rs.getString("chunk_id"),
                                rs.getString("content"),
                                score,
                                read(rs.getString("metadata"), METADATA)));
                    }
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("failed to retrieve for tenant '" + tenant + "'", e);
        }
        scored.sort(Comparator.comparingDouble(RetrievedChunk::score).reversed());
        return scored.size() > limit ? List.copyOf(scored.subList(0, limit)) : List.copyOf(scored);
    }

    private String write(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("failed to serialize value", e);
        }
    }

    private <T> T read(String json, Class<T> type) {
        try {
            return mapper.readValue(json, type);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("corrupt stored value", e);
        }
    }

    private <T> T read(String json, TypeReference<T> type) {
        try {
            return mapper.readValue(json, type);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("corrupt stored value", e);
        }
    }

    private static double cosine(float[] a, float[] b) {
        double dot = 0;
        double normA = 0;
        double normB = 0;
        int n = Math.min(a.length, b.length);
        for (int i = 0; i < n; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        return (normA == 0 || normB == 0) ? 0 : dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }
}
