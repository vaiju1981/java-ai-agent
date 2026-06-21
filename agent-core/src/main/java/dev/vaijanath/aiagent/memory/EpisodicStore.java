package dev.vaijanath.aiagent.memory;

import java.util.List;

/**
 * Long-term, cross-run memory of {@link Episode}s. The foundation for learning: an agent records what
 * went wrong and recalls relevant lessons before attempting a similar task again.
 *
 * <p>Recall is scoped to a tenant, so a lesson learned for one tenant is never surfaced to another.
 * The in-process implementation uses keyword relevance; a production store would back {@link #recall}
 * with a substrate vector store.
 */
public interface EpisodicStore {

    void record(Episode episode);

    /** Recall lessons relevant to {@code query} within {@code tenant}. */
    List<Episode> recall(String tenant, String query, int limit);

    /** Convenience for the {@code "default"} tenant. */
    default List<Episode> recall(String query, int limit) {
        return recall("default", query, limit);
    }
}
