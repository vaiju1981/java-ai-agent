package dev.vaijanath.aiagent.memory;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Shared keyword-overlap recall used by the episodic stores. */
final class Episodes {

    private Episodes() {}

    static List<Episode> recall(List<Episode> all, String query, int limit) {
        Set<String> q = tokens(query);
        if (q.isEmpty()) {
            return List.of();
        }
        return all.stream()
                .map(e -> Map.entry(e, overlap(q, tokens(e.task() + " " + e.lesson()))))
                .filter(entry -> entry.getValue() > 0)
                .sorted(Comparator.comparingInt(Map.Entry<Episode, Integer>::getValue).reversed())
                .limit(limit)
                .map(Map.Entry::getKey)
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
        return Arrays.stream(s.toLowerCase(Locale.ROOT).split("\\W+"))
                .filter(w -> w.length() >= 4)
                .collect(Collectors.toSet());
    }
}
