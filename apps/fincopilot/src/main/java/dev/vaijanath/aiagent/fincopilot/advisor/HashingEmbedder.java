package dev.vaijanath.aiagent.fincopilot.advisor;

import dev.vaijanath.aiagent.rag.Embedder;
import java.util.Locale;

/**
 * A deterministic, dependency-free {@link Embedder}: the hashing trick (each token maps to a dimension
 * by hash, with a sign to reduce collisions), then L2-normalised so cosine similarity is a plain dot
 * product. This gives lexical retrieval over the curated knowledge base with no model to pull; set a
 * dedicated Ollama embedding model later for semantic retrieval (the {@code Embedder} bean is swappable).
 */
public final class HashingEmbedder implements Embedder {

    private final int dimensions;

    public HashingEmbedder(int dimensions) {
        this.dimensions = Math.max(16, dimensions);
    }

    public HashingEmbedder() {
        this(256);
    }

    @Override
    public float[] embed(String text) {
        float[] vector = new float[dimensions];
        if (text == null || text.isBlank()) {
            return vector;
        }
        for (String token : text.toLowerCase(Locale.ROOT).split("[^a-z0-9]+")) {
            if (token.isEmpty()) {
                continue;
            }
            int hash = token.hashCode();
            int index = Math.floorMod(hash, dimensions);
            vector[index] += (hash & 1) == 0 ? 1f : -1f;
        }
        double norm = 0;
        for (float x : vector) {
            norm += x * x;
        }
        norm = Math.sqrt(norm);
        if (norm > 0) {
            for (int i = 0; i < vector.length; i++) {
                vector[i] /= (float) norm;
            }
        }
        return vector;
    }
}
