package dev.vaijanath.aiagent.store.pgvector;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vaijanath.aiagent.rag.Embedder;
import dev.vaijanath.aiagent.rag.RetrievedChunk;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

class PgVectorRetrieverTest {

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

    // --- pure logic (always runs, no database) ---

    @Test
    void formatsVectorLiterals() {
        assertEquals("[1.0,0.0,2.5]", PgVectorRetriever.vectorLiteral(new float[] {1f, 0f, 2.5f}));
        assertEquals("[]", PgVectorRetriever.vectorLiteral(new float[] {}));
    }

    @Test
    void convertsCosineDistanceToSimilarity() {
        assertEquals(1.0, PgVectorRetriever.similarity(0.0), 1e-9); // identical
        assertEquals(0.0, PgVectorRetriever.similarity(1.0), 1e-9); // orthogonal
    }

    @Test
    void rejectsNonPositiveDimensions() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new PgVectorRetriever(() -> null, bagOfWords("a"), 0));
    }

    // --- integration: runs in CI against the pgvector Postgres service ---

    @Test
    @EnabledIfEnvironmentVariable(named = "POSTGRES_TEST_URL", matches = ".+")
    void storesAndRetrievesViaPgvectorAnn() {
        PgVectorRetriever store =
                PgVectorRetriever.fromJdbcUrl(System.getenv("POSTGRES_TEST_URL"), bagOfWords("cat", "dog", "fish"), 3);
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
