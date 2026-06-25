package dev.vaijanath.aiagent.deep;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class DagTest {

    @Test
    void validateAcceptsAValidDag() {
        List<PlanStep> steps = List.of(
                new PlanStep(1, "a"),
                new PlanStep(2, "b", List.of(1)),
                new PlanStep(3, "c", List.of(1, 2)));

        Dag.validate(steps); // no exception
    }

    @Test
    void validateRejectsAnUnknownDependency() {
        List<PlanStep> steps = List.of(new PlanStep(1, "a", List.of(9)));

        assertThrows(IllegalArgumentException.class, () -> Dag.validate(steps));
    }

    @Test
    void validateRejectsASelfDependency() {
        List<PlanStep> steps = List.of(new PlanStep(1, "a", List.of(1)));

        assertThrows(IllegalArgumentException.class, () -> Dag.validate(steps));
    }

    @Test
    void validateRejectsACycle() {
        List<PlanStep> steps = List.of(
                new PlanStep(1, "a", List.of(2)),
                new PlanStep(2, "b", List.of(1)));

        assertThrows(IllegalArgumentException.class, () -> Dag.validate(steps));
    }

    @Test
    void readyReturnsOnlyPendingStepsWithAllDepsDone() {
        PlanStep a = new PlanStep(1, "a");
        PlanStep b = new PlanStep(2, "b", List.of(1));
        List<PlanStep> steps = List.of(a, b);

        assertEquals(List.of(1), indices(Dag.ready(steps)));

        a.status(PlanStep.Status.DONE);
        assertEquals(List.of(2), indices(Dag.ready(steps)));

        b.status(PlanStep.Status.RUNNING);
        assertEquals(List.of(), indices(Dag.ready(steps)));
    }

    private static List<Integer> indices(List<PlanStep> steps) {
        return steps.stream().map(PlanStep::index).toList();
    }
}
