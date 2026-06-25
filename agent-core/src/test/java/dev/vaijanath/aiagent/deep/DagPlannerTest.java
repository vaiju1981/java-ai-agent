package dev.vaijanath.aiagent.deep;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.vaijanath.aiagent.model.ModelRequest;
import dev.vaijanath.aiagent.model.StructuredOutput;
import java.util.List;
import org.junit.jupiter.api.Test;

class DagPlannerTest {

    @Test
    void mapsNodesToStepsWithDependencies() {
        DagPlanner.Graph graph = new DagPlanner.Graph(List.of(
                new DagPlanner.Node("research", List.of()),
                new DagPlanner.Node("summarize", List.of(1))));

        Plan plan = new DagPlanner(fixed(graph)).plan("task");

        assertEquals(2, plan.steps().size());
        assertEquals(List.of(), plan.steps().get(0).dependsOn());
        assertEquals(List.of(1), plan.steps().get(1).dependsOn());
    }

    @Test
    void dropsSelfAndForwardEdgesSoTheResultIsAlwaysADag() {
        DagPlanner.Graph graph = new DagPlanner.Graph(List.of(
                new DagPlanner.Node("a", List.of(1, 2)), // self (1) + forward (2): both dropped
                new DagPlanner.Node("b", List.of(1)))); // valid backward edge

        Plan plan = new DagPlanner(fixed(graph)).plan("task");

        assertEquals(List.of(), plan.steps().get(0).dependsOn());
        assertEquals(List.of(1), plan.steps().get(1).dependsOn());
        Dag.validate(plan.steps()); // the produced plan always validates
    }

    @Test
    void skipsBlankNodesButKeepsDependencyNumbering() {
        DagPlanner.Graph graph = new DagPlanner.Graph(List.of(
                new DagPlanner.Node("real", List.of()),
                new DagPlanner.Node("  ", List.of()), // position 2 dropped
                new DagPlanner.Node("needs 1", List.of(1)))); // still refers to position 1

        Plan plan = new DagPlanner(fixed(graph)).plan("task");

        assertEquals(2, plan.steps().size());
        assertEquals("real", plan.steps().get(0).description());
        assertEquals(1, plan.steps().get(0).index());
        assertEquals(3, plan.steps().get(1).index()); // position 2 dropped; numbering preserved
        assertEquals(List.of(1), plan.steps().get(1).dependsOn());
    }

    @Test
    void emptyGraphYieldsAnEmptyPlan() {
        assertEquals(0, new DagPlanner(fixed(new DagPlanner.Graph(List.of()))).plan("t").steps().size());
    }

    /** A {@link StructuredOutput} that always binds to the same {@link DagPlanner.Graph}. */
    private static StructuredOutput fixed(DagPlanner.Graph graph) {
        return new StructuredOutput() {
            @Override
            public <T> T generate(ModelRequest request, Class<T> type) {
                return type.cast(graph);
            }
        };
    }
}
