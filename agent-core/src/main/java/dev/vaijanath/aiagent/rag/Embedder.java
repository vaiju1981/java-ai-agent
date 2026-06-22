package dev.vaijanath.aiagent.rag;

/**
 * Turns text into a dense vector for semantic similarity — the L0 seam for retrieval. Implementations
 * wrap a substrate embedding model (e.g. a LangChain4j {@code EmbeddingModel}); agent-core stays
 * dependency-free and works only against this interface.
 */
@FunctionalInterface
public interface Embedder {

    float[] embed(String text);
}
