package dev.vaijanath.aiagent.store.pgvector;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vaijanath.aiagent.rag.Embedder;
import dev.vaijanath.aiagent.rag.RetrievedChunk;
import dev.vaijanath.aiagent.store.jdbc.ConnectionSource;
import java.sql.DriverManager;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * The live ANN path — {@code ensureSchema} → {@code add} → {@code retrieve} — exercised against a real
 * PostgreSQL + pgvector instance started by Testcontainers. Self-contained: it spins its own container
 * (no external service or {@code POSTGRES_TEST_URL} wiring), and is skipped automatically wherever
 * Docker is unavailable.
 */
@Testcontainers(disabledWithoutDocker = true)
class PgVectorRetrieverContainerTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg17").asCompatibleSubstituteFor("postgres"));

    private static Embedder bagOfWords(String... vocab) {
        List<String> words = List.of(vocab);
        return text -> {
            float[] v = new float[words.size()];
            String lower = text.toLowerCase();
            for (int i = 0; i < words.size(); i++) {
                if (lower.contains(words.get(i))) {
                    v[i] = 1f;
                }
            }
            return v;
        };
    }

    @Test
    void storesAndRetrievesViaPgvectorAnn() {
        ConnectionSource connections = () -> DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        PgVectorRetriever store = new PgVectorRetriever(connections, bagOfWords("cat", "dog", "fish"), 3);
        store.ensureSchema();

        String tenant = "pgv-" + UUID.randomUUID();
        store.add(tenant, "c1", "the cat", Map.of("source", "doc-1"));
        store.add(tenant, "c2", "the dog");
        store.add(tenant, "c3", "a fish");

        List<RetrievedChunk> hits = store.retrieve(tenant, "where is the cat?", 2);

        assertFalse(hits.isEmpty());
        assertEquals("c1", hits.get(0).id(), "the cat chunk is the nearest neighbour");
        assertEquals("doc-1", hits.get(0).metadata().get("source"));
        assertTrue(hits.get(0).score() >= hits.get(hits.size() - 1).score());
    }
}
