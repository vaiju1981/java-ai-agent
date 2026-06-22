package dev.vaijanath.aiagent.rag;

import java.util.List;

/**
 * Retrieves the most relevant chunks of context for a query — the seam a RAG agent uses to ground its
 * answer in real sources. Scoped to a tenant so one tenant's documents are never surfaced to another
 * (mirroring {@link dev.vaijanath.aiagent.memory.EpisodicStore}). Implementations may be in-memory,
 * JDBC-backed, or fronted by a substrate vector store.
 */
public interface Retriever {

    /** The {@code limit} most relevant chunks for {@code query} within {@code tenant}, best first. */
    List<RetrievedChunk> retrieve(String tenant, String query, int limit);

    /** Convenience for the {@code "default"} tenant. */
    default List<RetrievedChunk> retrieve(String query, int limit) {
        return retrieve("default", query, limit);
    }
}
