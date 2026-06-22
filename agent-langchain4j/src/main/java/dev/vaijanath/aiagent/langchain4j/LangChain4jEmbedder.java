package dev.vaijanath.aiagent.langchain4j;

import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.vaijanath.aiagent.rag.Embedder;
import java.util.Objects;

/**
 * An {@link Embedder} backed by any LangChain4j {@link EmbeddingModel} — bridges a real embedding
 * model into the dependency-free retrieval seam (e.g. for {@code InMemoryVectorStore} or
 * {@code JdbcVectorStore}).
 */
public final class LangChain4jEmbedder implements Embedder {

    private final EmbeddingModel model;

    public LangChain4jEmbedder(EmbeddingModel model) {
        this.model = Objects.requireNonNull(model, "model");
    }

    @Override
    public float[] embed(String text) {
        return model.embed(text).content().vector();
    }
}
