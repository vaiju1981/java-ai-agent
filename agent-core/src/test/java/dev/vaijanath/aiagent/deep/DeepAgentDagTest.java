package dev.vaijanath.aiagent.deep;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vaijanath.aiagent.agent.Agent;
import dev.vaijanath.aiagent.agent.AgentRequest;
import dev.vaijanath.aiagent.agent.AgentResponse;
import dev.vaijanath.aiagent.model.ModelPort;
import dev.vaijanath.aiagent.model.ModelResponse;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

class DeepAgentDagTest {

    private static final ModelPort SYNTH = request -> ModelResponse.text("ok");
    // A worker that echoes its input, so a step's persisted result reveals exactly what it saw.
    private static final Supplier<Agent> ECHO =
            () -> request -> AgentResponse.completed("[" + request.input() + "]");

    @Test
    void dependentStepReceivesUpstreamResults() {
        Planner planner = task -> new Plan(List.of(
                new PlanStep(1, "find the capital of France"),
                new PlanStep(2, "use the capital", List.of(1))));
        InMemoryWorkspace ws = new InMemoryWorkspace();

        DeepAgent.builder().planner(planner).worker(ECHO).synthesizer(SYNTH).workspace(ws).build()
                .run(new AgentRequest("t"));

        String step2 = ws.read("step-2.txt").orElse("");
        assertTrue(step2.contains("Context from prior steps:"), step2);
        assertTrue(step2.contains("[find the capital of France]"), step2); // step 1's actual result
    }

    @Test
    void independentStepGetsNoInjectedContext() {
        Planner planner = task -> new Plan(List.of(new PlanStep(1, "solo")));
        InMemoryWorkspace ws = new InMemoryWorkspace();

        DeepAgent.builder().planner(planner).worker(ECHO).synthesizer(SYNTH).workspace(ws).build()
                .run(new AgentRequest("t"));

        assertEquals("[solo]", ws.read("step-1.txt").orElse(""));
    }

    @Test
    void respectsDependencyOrderEvenWhenParallel() {
        List<Integer> order = new CopyOnWriteArrayList<>();
        Planner planner = task -> new Plan(List.of(
                new PlanStep(1, "A"),
                new PlanStep(2, "B", List.of(1))));
        Supplier<Agent> worker = () -> request -> {
            order.add(request.input().startsWith("A") ? 1 : 2);
            return AgentResponse.completed("done");
        };

        DeepAgent.builder().planner(planner).worker(worker).synthesizer(SYNTH).parallel(true).build()
                .run(new AgentRequest("t"));

        assertEquals(List.of(1, 2), order); // A's wave completes before B's wave starts
    }

    @Test
    void blockedStepIsSkippedWhenItsDependencyFails() {
        Planner planner = task -> new Plan(List.of(
                new PlanStep(1, "will fail"),
                new PlanStep(2, "depends on 1", List.of(1))));
        Supplier<Agent> worker = () -> request -> {
            if (request.input().startsWith("will fail")) {
                throw new IllegalStateException("boom");
            }
            return AgentResponse.completed("ran");
        };
        InMemoryWorkspace ws = new InMemoryWorkspace();

        DeepAgent.builder().planner(planner).worker(worker).synthesizer(SYNTH).parallel(false)
                .workspace(ws).build()
                .run(new AgentRequest("t"));

        assertTrue(ws.read("step-1.txt").orElse("").contains("failed"), "step 1 should have failed");
        assertTrue(ws.read("step-2.txt").orElse("").contains("skipped"), "step 2 should be skipped");
    }

    @Test
    void rejectsAPlanWithACycleBeforeRunning() {
        Planner planner = task -> new Plan(List.of(
                new PlanStep(1, "a", List.of(2)),
                new PlanStep(2, "b", List.of(1))));

        DeepAgent deep =
                DeepAgent.builder().planner(planner).worker(ECHO).synthesizer(SYNTH).build();

        assertThrows(IllegalArgumentException.class, () -> deep.run(new AgentRequest("t")));
    }
}
