package dev.vaijanath.aiagent.demos.support;

import dev.vaijanath.aiagent.demos.support.TicketTriager.TriageResult;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/** Aggregates triage results into simple counts (deterministic, unit-tested). */
final class TicketStats {

    private TicketStats() {}

    static Map<String, Long> countBy(List<TriageResult> results, java.util.function.Function<TriageResult, String> key) {
        return results.stream()
                .collect(Collectors.groupingBy(key, TreeMap::new, Collectors.counting()));
    }

    static String summary(List<TriageResult> results) {
        Map<String, Long> byCategory = countBy(results, TriageResult::category);
        Map<String, Long> byPriority = countBy(results, TriageResult::priority);
        return "by category: " + byCategory + "\nby priority: " + byPriority;
    }
}
