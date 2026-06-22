package dev.vaijanath.aiagent.store.pgvector;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.vaijanath.aiagent.rag.Embedder;
import dev.vaijanath.aiagent.rag.RetrievedChunk;
import dev.vaijanath.aiagent.rag.Retriever;
import dev.vaijanath.aiagent.store.jdbc.ConnectionSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A {@link Retriever} backed by PostgreSQL + the <a href="https://github.com/pgvector/pgvector">
 * pgvector</a> extension: embeddings live in a native {@code vector} column and are ranked by cosine
 * distance ({@code <=>}) using an HNSW index — so approximate-nearest-neighbour search scales to large
 * corpora, unlike the in-application cosine of {@code JdbcVectorStore}. The {@link Embedder}'s output
 * dimensionality is fixed at construction (the {@code vector(n)} column needs it).
 *
 * <p>{@link #ensureSchema()} creates the extension, table, and index. Requires a PostgreSQL with
 * pgvector available (e.g. the {@code pgvector/pgvector} image).
 */
public final class PgVectorRetriever implements Retriever {

    private static final TypeReference<Map<String, String>> METADATA = new TypeReference<>() {};

    private final ConnectionSource connections;
    private final Embedder embedder;
    private final int dimensions;
    private final ObjectMapper mapper = new ObjectMapper();

    public PgVectorRetriever(ConnectionSource connections, Embedder embedder, int dimensions) {
        this.connections = Objects.requireNonNull(connections, "connections");
        this.embedder = Objects.requireNonNull(embedder, "embedder");
        // pgvector's HNSW index supports up to 2000 dimensions; bound here so the value that flows into
        // the vector(n) DDL is a validated small integer, and so we fail fast with a clear message rather
        // than at index-creation time.
        if (dimensions < 1 || dimensions > 2000) {
            throw new IllegalArgumentException("dimensions must be in [1, 2000], got " + dimensions);
        }
        this.dimensions = dimensions;
    }

    /** A local convenience over a {@code DriverManager} URL that also creates the schema. */
    public static PgVectorRetriever fromJdbcUrl(String url, Embedder embedder, int dimensions) {
        PgVectorRetriever store =
                new PgVectorRetriever(() -> DriverManager.getConnection(url), embedder, dimensions);
        store.ensureSchema();
        return store;
    }

    /** Creates the pgvector extension, the {@code rag_vectors} table, and an HNSW cosine index. */
    public void ensureSchema() {
        // The only dynamic part of this DDL is the vector dimension — a validated int in [1, 2000]
        // (see the constructor), never user-supplied text. A column type modifier cannot be bound as a
        // statement parameter, so it is formatted into the DDL directly.
        String createTable = "CREATE TABLE IF NOT EXISTS rag_vectors ("
                + "tenant VARCHAR(128) NOT NULL,"
                + "chunk_id VARCHAR(256) NOT NULL,"
                + "content TEXT NOT NULL,"
                + "metadata TEXT NOT NULL,"
                + "embedding vector(" + dimensions + ") NOT NULL,"
                + "PRIMARY KEY (tenant, chunk_id))";
        try (Connection c = connections.get();
                Statement st = c.createStatement()) {
            st.execute("CREATE EXTENSION IF NOT EXISTS vector");
            st.execute(createTable); // NOSONAR: dimension is a validated int (see above), not user input
            st.execute("CREATE INDEX IF NOT EXISTS rag_vectors_embedding_idx "
                    + "ON rag_vectors USING hnsw (embedding vector_cosine_ops)");
        } catch (SQLException e) {
            throw new IllegalStateException("failed to create pgvector schema", e);
        }
    }

    /** Embeds and upserts a chunk under {@code tenant}. */
    public void add(String tenant, String id, String text, Map<String, String> metadata) {
        Objects.requireNonNull(tenant, "tenant");
        Objects.requireNonNull(id, "id");
        float[] vector = embedder.embed(text);
        if (vector.length != dimensions) {
            throw new IllegalArgumentException(
                    "embedding has " + vector.length + " dimensions, expected " + dimensions);
        }
        try (Connection c = connections.get();
                PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO rag_vectors (tenant, chunk_id, content, metadata, embedding) "
                                + "VALUES (?, ?, ?, ?, CAST(? AS vector)) "
                                + "ON CONFLICT (tenant, chunk_id) DO UPDATE SET content = excluded.content, "
                                + "metadata = excluded.metadata, embedding = excluded.embedding")) {
            ps.setString(1, tenant);
            ps.setString(2, id);
            ps.setString(3, text == null ? "" : text);
            ps.setString(4, writeMetadata(metadata));
            ps.setString(5, vectorLiteral(vector));
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
        String queryVector = vectorLiteral(embedder.embed(query));
        List<RetrievedChunk> hits = new ArrayList<>();
        try (Connection c = connections.get();
                PreparedStatement ps = c.prepareStatement(
                        "SELECT chunk_id, content, metadata, embedding <=> CAST(? AS vector) AS distance "
                                + "FROM rag_vectors WHERE tenant = ? "
                                + "ORDER BY embedding <=> CAST(? AS vector) LIMIT ?")) {
            ps.setString(1, queryVector);
            ps.setString(2, tenant);
            ps.setString(3, queryVector);
            ps.setInt(4, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    hits.add(new RetrievedChunk(
                            rs.getString("chunk_id"),
                            rs.getString("content"),
                            similarity(rs.getDouble("distance")),
                            readMetadata(rs.getString("metadata"))));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("failed to retrieve for tenant '" + tenant + "'", e);
        }
        return hits;
    }

    /** pgvector's {@code <=>} is cosine <em>distance</em> in [0,2]; convert to similarity (higher = better). */
    static double similarity(double cosineDistance) {
        return 1.0 - cosineDistance;
    }

    /** Formats a vector as a pgvector text literal: {@code [0.1,0.2,0.3]}. */
    static String vectorLiteral(float[] vector) {
        StringBuilder sb = new StringBuilder(vector.length * 8 + 2).append('[');
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(vector[i]);
        }
        return sb.append(']').toString();
    }

    private String writeMetadata(Map<String, String> metadata) {
        try {
            return mapper.writeValueAsString(metadata == null ? Map.of() : metadata);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("metadata is not serializable", e);
        }
    }

    private Map<String, String> readMetadata(String json) {
        try {
            return mapper.readValue(json, METADATA);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("corrupt stored metadata", e);
        }
    }
}
