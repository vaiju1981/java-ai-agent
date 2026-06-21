package dev.vaijanath.aiagent.memory;

import java.util.List;

/**
 * Long-term, cross-run memory of {@link Episode}s. The foundation for learning: an agent records
 * what went wrong and recalls relevant lessons before attempting a similar task again.
 *
 * <p>The in-process implementation uses keyword relevance; a production store would back
 * {@link #recall} with a substrate vector store.
 */
public interface EpisodicStore {

    void record(Episode episode);

    List<Episode> recall(String query, int limit);
}
