package dev.vaijanath.aiagent.deep;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Dependency-graph helpers for a {@link Plan} whose {@link PlanStep#dependsOn()} edges form a DAG.
 * Package-private: the scheduling itself lives in {@link DeepAgent}; this isolates the pure graph
 * logic so it can be reasoned about and tested on its own.
 */
final class Dag {

    private Dag() {}

    /**
     * Validates that every dependency refers to an existing step, no step depends on itself, and the
     * graph is acyclic — so a wave scheduler is guaranteed to make progress.
     *
     * @throws IllegalArgumentException if a dependency is unknown, self-referential, or forms a cycle
     */
    static void validate(List<PlanStep> steps) {
        Set<Integer> indices = new HashSet<>();
        for (PlanStep step : steps) {
            indices.add(step.index());
        }
        Map<Integer, Integer> indegree = new HashMap<>();
        Map<Integer, List<Integer>> dependents = new HashMap<>();
        for (PlanStep step : steps) {
            indegree.put(step.index(), step.dependsOn().size());
            for (int dep : step.dependsOn()) {
                if (dep == step.index()) {
                    throw new IllegalArgumentException("step " + step.index() + " depends on itself");
                }
                if (!indices.contains(dep)) {
                    throw new IllegalArgumentException(
                            "step " + step.index() + " depends on unknown step " + dep);
                }
                dependents.computeIfAbsent(dep, k -> new ArrayList<>()).add(step.index());
            }
        }
        // Kahn's algorithm: if we can't peel every node, a cycle remains.
        Deque<Integer> queue = new ArrayDeque<>();
        indegree.forEach((index, degree) -> {
            if (degree == 0) {
                queue.add(index);
            }
        });
        int peeled = 0;
        while (!queue.isEmpty()) {
            int index = queue.poll();
            peeled++;
            for (int dependent : dependents.getOrDefault(index, List.of())) {
                if (indegree.merge(dependent, -1, Integer::sum) == 0) {
                    queue.add(dependent);
                }
            }
        }
        if (peeled != steps.size()) {
            throw new IllegalArgumentException("plan has a dependency cycle");
        }
    }

    /** The steps that are {@code PENDING} and whose dependencies are all {@code DONE}. */
    static List<PlanStep> ready(List<PlanStep> steps) {
        Map<Integer, PlanStep.Status> status = new HashMap<>();
        for (PlanStep step : steps) {
            status.put(step.index(), step.status());
        }
        List<PlanStep> ready = new ArrayList<>();
        for (PlanStep step : steps) {
            if (step.status() != PlanStep.Status.PENDING) {
                continue;
            }
            boolean allDepsDone = step.dependsOn().stream()
                    .allMatch(dep -> status.get(dep) == PlanStep.Status.DONE);
            if (allDepsDone) {
                ready.add(step);
            }
        }
        return ready;
    }
}
