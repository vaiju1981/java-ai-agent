package dev.vaijanath.aiagent.store.pgvector;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vaijanath.aiagent.rag.Embedder;
import dev.vaijanath.aiagent.store.jdbc.ConnectionSource;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Pure-logic and guard-path tests that need no database (always run). The live ANN path
 * ({@code ensureSchema}/{@code add}/{@code retrieve}) is covered against a real pgvector instance in
 * {@link PgVectorRetrieverContainerTest}.
 */
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

    /** A connection source that fails if used — proves a code path never opens a database connection. */
    private static ConnectionSource noDatabase() {
        return () -> {
            throw new AssertionError("must not open a database connection");
        };
    }

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
                IllegalArgumentException.class, () -> new PgVectorRetriever(noDatabase(), bagOfWords("a"), 0));
    }

    @Test
    void rejectsDimensionsAbovePgvectorHnswLimit() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new PgVectorRetriever(noDatabase(), bagOfWords("a"), 2001));
    }

    @Test
    void retrieveWithNonPositiveLimitReturnsEmptyWithoutTouchingTheDatabase() {
        PgVectorRetriever store = new PgVectorRetriever(noDatabase(), bagOfWords("a"), 1);
        assertTrue(store.retrieve("t", "q", 0).isEmpty());
        assertTrue(store.retrieve("t", "q", -1).isEmpty());
    }

    @Test
    void addRejectsEmbeddingWithWrongDimensionWithoutTouchingTheDatabase() {
        // The embedder yields a length-2 vector but the store is configured for 3 dimensions.
        PgVectorRetriever store = new PgVectorRetriever(noDatabase(), bagOfWords("a", "b"), 3);
        assertThrows(IllegalArgumentException.class, () -> store.add("t", "id", "a b"));
    }
}
