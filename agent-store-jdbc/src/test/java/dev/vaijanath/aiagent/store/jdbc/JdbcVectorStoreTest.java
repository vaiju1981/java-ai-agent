package dev.vaijanath.aiagent.store.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.vaijanath.aiagent.rag.Embedder;
import dev.vaijanath.aiagent.rag.RetrievedChunk;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;

class JdbcVectorStoreTest {

    /** A deterministic bag-of-words embedder: one dimension per vocab word, 1 if present. */
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
    void persistsAndRetrievesRankedTenantScoped(@TempDir Path dir) {
        String url = "jdbc:sqlite:" + dir.resolve("rag.db");
        JdbcVectorStore store = JdbcVectorStore.fromJdbcUrl(url, bagOfWords("cat", "dog", "fish"));
        store.add("t1", "c1", "the cat sat on the mat", Map.of("source", "doc-1"));
        store.add("t1", "c2", "a cat chased a dog");
        store.add("t2", "c3", "another tenant's cat");

        // A fresh instance over the same DB stands in for a restart.
        JdbcVectorStore reopened = JdbcVectorStore.fromJdbcUrl(url, bagOfWords("cat", "dog", "fish"));
        List<RetrievedChunk> hits = reopened.retrieve("t1", "where is the cat?", 5);

        assertEquals(List.of("c1", "c2"), hits.stream().map(RetrievedChunk::id).toList());
        assertEquals("doc-1", hits.get(0).metadata().get("source"));
    }

    @Test
    void upsertReplacesAChunkInPlace(@TempDir Path dir) {
        String url = "jdbc:sqlite:" + dir.resolve("rag.db");
        JdbcVectorStore store = JdbcVectorStore.fromJdbcUrl(url, bagOfWords("cat", "dog"));
        store.add("t1", "c1", "cat");
        store.add("t1", "c1", "cat dog"); // same id -> upsert

        List<RetrievedChunk> byDog = store.retrieve("t1", "dog", 5);
        assertEquals(List.of("c1"), byDog.stream().map(RetrievedChunk::id).toList());
        assertEquals("cat dog", byDog.get(0).text());
    }

    @Test
    void respectsTheLimit(@TempDir Path dir) {
        String url = "jdbc:sqlite:" + dir.resolve("rag.db");
        JdbcVectorStore store = JdbcVectorStore.fromJdbcUrl(url, bagOfWords("cat", "dog"));
        store.add("t1", "c1", "cat");
        store.add("t1", "c2", "cat and dog");

        assertEquals(1, store.retrieve("t1", "cat dog", 1).size());
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "POSTGRES_TEST_URL", matches = ".+")
    void worksOnPostgres() {
        JdbcVectorStore store =
                JdbcVectorStore.fromJdbcUrl(System.getenv("POSTGRES_TEST_URL"), bagOfWords("cat", "dog"));
        String tenant = "rag-" + UUID.randomUUID();
        store.add(tenant, "c1", "the cat");
        store.add(tenant, "c2", "the dog");

        assertEquals(List.of("c1"), store.retrieve(tenant, "cat", 5).stream().map(RetrievedChunk::id).toList());
    }
}
