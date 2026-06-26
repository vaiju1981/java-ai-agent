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

    // A plain class rather than a record: the float[] vector would make a record's generated
    // equals/hashCode use array identity (we never compare entries, but the seam stays clean).
    private static final class Entry {
        final String tenant;
        final String id;
        final String text;
        final Map<String, String> metadata;
        final float[] vector;

        Entry(String tenant, String id, String text, Map<String, String> metadata, float[] vector) {
            this.tenant = tenant;
            this.id = id;
            this.text = text;
            this.metadata = metadata;
            this.vector = vector;
        }
    }

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
                .filter(e -> e.tenant.equals(tenant))
                .map(e -> new RetrievedChunk(e.id, e.text, Vectors.cosine(q, e.vector), e.metadata))
                .filter(c -> c.score() > 0.0)
                .sorted(Comparator.comparingDouble(RetrievedChunk::score).reversed())
                .limit(limit)
                .toList();
    }
}
