package dev.vaijanath.aiagent.deep;

import java.util.List;

/** An ordered list of subtasks for a deep agent. */
public record Plan(List<PlanStep> steps) {

    public Plan {
        steps = List.copyOf(steps);
    }

    public boolean isEmpty() {
        return steps.isEmpty();
    }

    /** Renders the plan as a checklist (handy as the {@code plan.md} workspace artifact). */
    public String render() {
        StringBuilder sb = new StringBuilder();
        for (PlanStep s : steps) {
            sb.append('[').append(s.status()).append("] ")
                    .append(s.index()).append(". ").append(s.description()).append('\n');
        }
        return sb.toString();
    }
}
