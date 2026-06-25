package dev.vaijanath.aiagent.checkpoint;

import java.util.List;

/**
 * An immutable snapshot of a step-based orchestration: the original task plus the state of each step.
 * An orchestrator rebuilds its plan from this to resume — re-running only the steps that are not yet
 * {@code DONE}, with the completed steps' results already in hand.
 */
public record Checkpoint(String task, List<Step> steps) {

    public Checkpoint {
        steps = steps == null ? List.of() : List.copyOf(steps);
    }

    /**
     * One step's persisted state: its index, description, the indices it depends on, its
     * {@code PlanStep.Status} name, and its result so far.
     */
    public record Step(int index, String description, List<Integer> dependsOn, String status, String result) {

        public Step {
            dependsOn = dependsOn == null ? List.of() : List.copyOf(dependsOn);
        }
    }
}
