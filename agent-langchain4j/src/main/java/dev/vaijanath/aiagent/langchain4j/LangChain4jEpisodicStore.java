package dev.vaijanath.aiagent.langchain4j;

import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.vaijanath.aiagent.memory.Episode;
import dev.vaijanath.aiagent.memory.EpisodicStore;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A semantic {@link EpisodicStore}: episodes are recalled by embedding similarity (cosine) rather
 * than keyword overlap, so a lesson surfaces even when the new task is worded differently. Uses any
 * LangChain4j {@code EmbeddingModel}. Vectors are held in memory; wrap with a persistent store, or
 * back this with a substrate vector store, for durability at scale.
 */
public final class LangChain4jEpisodicStore implements EpisodicStore {

    private record Entry(Episode episode, float[] vector) {
    }

    private final EmbeddingModel embeddings;
    private final List<Entry> entries = new CopyOnWriteArrayList<>();

    public LangChain4jEpisodicStore(EmbeddingModel embeddings) {
        this.embeddings = Objects.requireNonNull(embeddings, "embeddings");
    }

    @Override
    public void record(Episode episode) {
        entries.add(new Entry(episode, embed(episode.task() + " " + episode.lesson())));
    }

    @Override
    public List<Episode> recall(String query, int limit) {
        if (entries.isEmpty()) {
            return List.of();
        }
        float[] q = embed(query);
        return entries.stream()
                .map(e -> Map.entry(e.episode(), cosine(q, e.vector())))
                .filter(m -> m.getValue() > 0.0)
                .sorted(Comparator.comparingDouble(Map.Entry<Episode, Double>::getValue).reversed())
                .limit(limit)
                .map(Map.Entry::getKey)
                .toList();
    }

    private float[] embed(String text) {
        return embeddings.embed(text).content().vector();
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
