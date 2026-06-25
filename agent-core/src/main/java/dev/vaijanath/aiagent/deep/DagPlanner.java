package dev.vaijanath.aiagent.deep;

import dev.vaijanath.aiagent.model.Message;
import dev.vaijanath.aiagent.model.ModelRequest;
import dev.vaijanath.aiagent.model.StructuredOutput;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A {@link Planner} that asks a model for a <b>DAG</b> of subtasks — each with its dependencies — so a
 * {@link DeepAgent} can run independent steps in parallel while feeding each dependent step the
 * results of the steps it depends on. For a flat list of independent subtasks, use {@link LlmPlanner}.
 *
 * <p>Uses {@link StructuredOutput}, so the model returns JSON bound directly to {@link Graph} (no
 * parsing). The result is always a valid DAG: dependencies are kept only when they point to an
 * earlier, non-blank step, so self-references, forward references, and cycles are dropped rather than
 * failing the run.
 */
public final class DagPlanner implements Planner {

    /** The structured plan the model fills in. */
    public record Graph(List<Node> subtasks) {
    }

    /** One subtask and the 1-based indices of the earlier subtasks whose results it needs. */
    public record Node(String description, List<Integer> dependsOn) {
    }

    private final StructuredOutput structured;
    private final int maxSteps;

    public DagPlanner(StructuredOutput structured) {
        this(structured, 8);
    }

    public DagPlanner(StructuredOutput structured, int maxSteps) {
        this.structured = Objects.requireNonNull(structured, "structured");
        this.maxSteps = maxSteps;
    }

    @Override
    public Plan plan(String task) {
        String prompt = "Break the task into at most " + maxSteps + " subtasks that form a DAG. Number "
                + "them 1..N in the order you list them. For each subtask, put in \"dependsOn\" the "
                + "numbers of the EARLIER subtasks whose results it needs (empty if independent). Keep "
                + "independent subtasks dependency-free so they can run in parallel.\nTask: " + task;
        Graph graph = structured.generate(ModelRequest.of(List.of(Message.user(prompt))), Graph.class);

        List<Node> nodes = (graph == null || graph.subtasks() == null) ? List.of() : graph.subtasks();
        // Keep the model's 1-based numbering so dependsOn references line up; skip blank entries.
        Map<Integer, Node> kept = new LinkedHashMap<>();
        int position = 0;
        for (Node node : nodes) {
            position++;
            if (kept.size() >= maxSteps) {
                break;
            }
            if (node != null && node.description() != null && !node.description().isBlank()) {
                kept.put(position, node);
            }
        }

        List<PlanStep> steps = new ArrayList<>();
        for (Map.Entry<Integer, Node> entry : kept.entrySet()) {
            int index = entry.getKey();
            List<Integer> deps = entry.getValue().dependsOn() == null
                    ? List.of()
                    : entry.getValue().dependsOn().stream()
                            .filter(kept::containsKey)
                            .filter(dep -> dep < index) // only backward edges -> always acyclic
                            .distinct()
                            .toList();
            steps.add(new PlanStep(index, entry.getValue().description().strip(), deps));
        }
        return new Plan(steps);
    }
}
