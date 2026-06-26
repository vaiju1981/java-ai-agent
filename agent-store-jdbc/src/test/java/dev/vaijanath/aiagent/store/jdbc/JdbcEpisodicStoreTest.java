package dev.vaijanath.aiagent.store.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vaijanath.aiagent.memory.Episode;
import dev.vaijanath.aiagent.rag.Embedder;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JdbcEpisodicStoreTest {

    /** A deterministic bag-of-words embedder: one dimension per vocab word, 1 if the text contains it. */
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
    void recordsAndRecallsSemanticallyAcrossRestart(@TempDir Path dir) {
        String url = "jdbc:sqlite:" + dir.resolve("episodes.db");
        Embedder emb = bagOfWords("refund", "shipping", "discount");
        JdbcEpisodicStore store = JdbcEpisodicStore.fromJdbcUrl(url, emb);
        store.record(new Episode("t1", "process a refund for an order", "used the wrong API", false,
                "call the refund endpoint, not payments"));
        store.record(new Episode("t1", "apply a discount code", "ok", true, "discount codes are case-insensitive"));

        // A fresh instance over the same DB stands in for a restart.
        JdbcEpisodicStore reopened = JdbcEpisodicStore.fromJdbcUrl(url, emb);
        List<Episode> hits = reopened.recall("t1", "how do I refund a customer?", 3);

        assertEquals(1, hits.size(), "only the semantically-similar (refund) episode is recalled");
        assertTrue(hits.get(0).lesson().contains("refund endpoint"));
    }

    @Test
    void recallIsTenantScoped(@TempDir Path dir) {
        String url = "jdbc:sqlite:" + dir.resolve("episodes.db");
        Embedder emb = bagOfWords("refund");
        JdbcEpisodicStore store = JdbcEpisodicStore.fromJdbcUrl(url, emb);
        store.record(new Episode("t1", "refund flow", "x", false, "t1 lesson about refund"));
        store.record(new Episode("t2", "refund flow", "x", false, "t2 lesson about refund"));

        assertEquals(
                List.of("t1 lesson about refund"),
                store.recall("t1", "refund", 5).stream().map(Episode::lesson).toList());
    }

    @Test
    void respectsTheLimit(@TempDir Path dir) {
        String url = "jdbc:sqlite:" + dir.resolve("episodes.db");
        Embedder emb = bagOfWords("refund");
        JdbcEpisodicStore store = JdbcEpisodicStore.fromJdbcUrl(url, emb);
        store.record(new Episode("t1", "refund a", "x", false, "lesson a refund"));
        store.record(new Episode("t1", "refund b", "x", false, "lesson b refund"));

        assertEquals(1, store.recall("t1", "refund", 1).size());
    }
}
