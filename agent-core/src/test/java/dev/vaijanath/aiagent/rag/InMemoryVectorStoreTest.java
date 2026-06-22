package dev.vaijanath.aiagent.rag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class InMemoryVectorStoreTest {

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
    void retrievesSimilarChunksRankedAndTenantScoped() {
        InMemoryVectorStore store = new InMemoryVectorStore(bagOfWords("cat", "dog", "fish"));
        store.add("t1", "c1", "the cat sat on the mat");
        store.add("t1", "c2", "a cat chased a dog");
        store.add("t1", "c3", "fish swim in water");
        store.add("t2", "other", "a cat in another tenant");

        List<RetrievedChunk> hits = store.retrieve("t1", "where is the cat?", 5);

        // c1 and c2 mention "cat" (positive cosine); c3 (fish) and t2's chunk are excluded.
        assertEquals(List.of("c1", "c2"), hits.stream().map(RetrievedChunk::id).toList());
        // c1 is a closer match to a cat-only query than c2 (which also carries "dog").
        assertTrue(hits.get(0).score() >= hits.get(1).score());
    }

    @Test
    void respectsTheLimit() {
        InMemoryVectorStore store = new InMemoryVectorStore(bagOfWords("cat", "dog"));
        store.add("t1", "c1", "cat");
        store.add("t1", "c2", "cat and dog");

        assertEquals(1, store.retrieve("t1", "cat dog", 1).size());
    }

    @Test
    void emptyStoreOrZeroLimitReturnsNothing() {
        InMemoryVectorStore store = new InMemoryVectorStore(bagOfWords("cat"));
        assertTrue(store.retrieve("t1", "cat", 5).isEmpty());

        store.add("t1", "c1", "cat");
        assertTrue(store.retrieve("t1", "cat", 0).isEmpty());
    }

    @Test
    void metadataIsCarriedThrough() {
        InMemoryVectorStore store = new InMemoryVectorStore(bagOfWords("cat"));
        store.add("t1", "c1", "the cat", java.util.Map.of("source", "doc-1"));

        RetrievedChunk hit = store.retrieve("t1", "cat", 1).get(0);
        assertEquals("doc-1", hit.metadata().get("source"));
    }
}
