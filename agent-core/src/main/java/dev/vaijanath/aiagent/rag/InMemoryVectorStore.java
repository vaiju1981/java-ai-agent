package dev.vaijanath.aiagent.rag;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * An in-memory {@link Retriever} that embeds added chunks and ranks them by cosine similarity to the
 * query — a working RAG store with no external dependencies, suitable for small corpora, tests, and
 * demos. Back retrieval with a JDBC or substrate vector store for durability and scale.
 */
public final class InMemoryVectorStore implements Retriever {

    private record Entry(String tenant, String id, String text, Map<String, String> metadata, float[] vector) {}

    private final Embedder embedder;
    private final List<Entry> entries = new CopyOnWriteArrayList<>();

    public InMemoryVectorStore(Embedder embedder) {
        this.embedder = Objects.requireNonNull(embedder, "embedder");
    }

    /** Embeds and stores a chunk under {@code tenant}; returns {@code this} for chaining. */
    public InMemoryVectorStore add(String tenant, String id, String text, Map<String, String> metadata) {
        Objects.requireNonNull(id, "id");
        entries.add(new Entry(
                tenant, id, text, metadata == null ? Map.of() : Map.copyOf(metadata), embedder.embed(text)));
        return this;
    }

    public InMemoryVectorStore add(String tenant, String id, String text) {
        return add(tenant, id, text, Map.of());
    }

    @Override
    public List<RetrievedChunk> retrieve(String tenant, String query, int limit) {
        if (entries.isEmpty() || limit <= 0) {
            return List.of();
        }
        float[] q = embedder.embed(query);
        return entries.stream()
                .filter(e -> e.tenant().equals(tenant))
                .map(e -> new RetrievedChunk(e.id(), e.text(), cosine(q, e.vector()), e.metadata()))
                .filter(c -> c.score() > 0.0)
                .sorted(Comparator.comparingDouble(RetrievedChunk::score).reversed())
                .limit(limit)
                .toList();
    }

    static double cosine(float[] a, float[] b) {
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
