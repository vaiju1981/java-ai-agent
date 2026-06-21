package dev.vaijanath.aiagent.memory;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/** A thread-safe episodic store with simple keyword-overlap relevance. */
public final class InMemoryEpisodicStore implements EpisodicStore {

    private final List<Episode> episodes = new CopyOnWriteArrayList<>();

    @Override
    public void record(Episode episode) {
        episodes.add(episode);
    }

    @Override
    public List<Episode> recall(String query, int limit) {
        Set<String> q = tokens(query);
        if (q.isEmpty()) {
            return List.of();
        }
        return episodes.stream()
                .map(e -> new Scored(e, overlap(q, tokens(e.task() + " " + e.lesson()))))
                .filter(s -> s.score > 0)
                .sorted(Comparator.comparingInt((Scored s) -> s.score).reversed())
                .limit(limit)
                .map(s -> s.episode)
                .toList();
    }

    private static int overlap(Set<String> a, Set<String> b) {
        int n = 0;
        for (String w : a) {
            if (b.contains(w)) {
                n++;
            }
        }
        return n;
    }

    private static Set<String> tokens(String s) {
        return java.util.Arrays.stream(s.toLowerCase(Locale.ROOT).split("\\W+"))
                .filter(w -> w.length() >= 4)
                .collect(Collectors.toSet());
    }

    private record Scored(Episode episode, int score) {
    }
}
