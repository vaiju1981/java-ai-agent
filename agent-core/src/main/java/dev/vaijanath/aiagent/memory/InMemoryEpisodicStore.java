package dev.vaijanath.aiagent.memory;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** A thread-safe, in-process episodic store with keyword-overlap relevance. Not persistent. */
public final class InMemoryEpisodicStore implements EpisodicStore {

    private final List<Episode> episodes = new CopyOnWriteArrayList<>();

    @Override
    public void record(Episode episode) {
        episodes.add(episode);
    }

    @Override
    public List<Episode> recall(String tenant, String query, int limit) {
        return Episodes.recall(List.copyOf(episodes), tenant, query, limit);
    }
}
