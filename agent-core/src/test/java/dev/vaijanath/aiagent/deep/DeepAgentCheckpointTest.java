package dev.vaijanath.aiagent.deep;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vaijanath.aiagent.agent.Agent;
import dev.vaijanath.aiagent.agent.AgentRequest;
import dev.vaijanath.aiagent.agent.AgentResponse;
import dev.vaijanath.aiagent.agent.RequestContext;
import dev.vaijanath.aiagent.checkpoint.CheckpointStore;
import dev.vaijanath.aiagent.checkpoint.InMemoryCheckpointStore;
import dev.vaijanath.aiagent.model.ModelPort;
import dev.vaijanath.aiagent.model.ModelResponse;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

class DeepAgentCheckpointTest {

    private static final ModelPort SYNTH = request -> ModelResponse.text("FINAL");

    /** Not a RuntimeException, so the worker loop does not catch it — it propagates like a real crash. */
    private static final class SimulatedCrash extends Error {
        SimulatedCrash(String message) {
            super(message);
        }
    }

    private static RequestContext ctx(String traceId) {
        return new RequestContext("session", "principal", "acme", traceId, null, Map.of());
    }

    @Test
    void resumesAfterACrashAndRerunsOnlyUnfinishedSteps() {
        CheckpointStore store = new InMemoryCheckpointStore();
        List<String> ran = new CopyOnWriteArrayList<>();
        AtomicBoolean crash = new AtomicBoolean(true);

        Planner planner = task -> new Plan(List.of(new PlanStep(1, "one"), new PlanStep(2, "two")));
        Supplier<Agent> worker = () -> request -> {
            if ("two".equals(request.input()) && crash.get()) {
                throw new SimulatedCrash("boom"); // crash on step 2, after step 1 was checkpointed
            }
            ran.add(request.input());
            return AgentResponse.completed("did:" + request.input());
        };
        DeepAgent deep = DeepAgent.builder().planner(planner).worker(worker).synthesizer(SYNTH)
                .parallel(false).checkpoints(store).build();

        // First attempt: step 1 completes and is saved, then step 2 crashes the run.
        assertThrows(SimulatedCrash.class, () -> deep.run(new AgentRequest("t", ctx("trace-1"))));
        assertEquals(List.of("one"), ran);
        assertTrue(store.load("acme", "trace-1").isPresent(), "checkpoint should survive the crash");

        // Retry with the SAME traceId: step 1 is restored (skipped), only step 2 re-runs.
        crash.set(false);
        AgentResponse r = deep.run(new AgentRequest("t", ctx("trace-1")));

        assertEquals("FINAL", r.output());
        assertEquals(List.of("one", "two"), ran); // "one" ran once total; "two" ran on the retry
        assertTrue(store.load("acme", "trace-1").isEmpty(), "checkpoint removed after clean completion");
    }

    @Test
    void deletesTheCheckpointOnCleanCompletion() {
        CheckpointStore store = new InMemoryCheckpointStore();
        Planner planner = task -> new Plan(List.of(new PlanStep(1, "a")));
        Supplier<Agent> worker = () -> request -> AgentResponse.completed("ok");

        DeepAgent.builder().planner(planner).worker(worker).synthesizer(SYNTH).checkpoints(store).build()
                .run(new AgentRequest("task", ctx("trace-2")));

        assertTrue(store.load("acme", "trace-2").isEmpty());
    }

    @Test
    void aFreshTraceIdDoesNotResumeAnotherRun() {
        CheckpointStore store = new InMemoryCheckpointStore();
        List<String> ran = new CopyOnWriteArrayList<>();
        Planner planner = task -> new Plan(List.of(new PlanStep(1, "only")));
        Supplier<Agent> worker = () -> request -> {
            ran.add(request.input());
            return AgentResponse.completed("ok");
        };
        DeepAgent deep = DeepAgent.builder().planner(planner).worker(worker).synthesizer(SYNTH)
                .checkpoints(store).build();

        deep.run(new AgentRequest("t", ctx("trace-A")));
        deep.run(new AgentRequest("t", ctx("trace-B")));

        assertEquals(2, ran.size()); // independent runs, each executed its step
    }

    @Test
    void withoutAStoreNothingIsPersisted() {
        Planner planner = task -> new Plan(List.of(new PlanStep(1, "a")));
        Supplier<Agent> worker = () -> request -> AgentResponse.completed("ok");

        AgentResponse r = DeepAgent.builder().planner(planner).worker(worker).synthesizer(SYNTH).build()
                .run(new AgentRequest("task", ctx("trace-3")));

        assertEquals("FINAL", r.output()); // unchanged behavior, no NPE without a store
    }

    @Test
    void resumePreservesDependenciesAndFeedsUpstreamResultsToDependents() {
        CheckpointStore store = new InMemoryCheckpointStore();
        InMemoryWorkspace ws = new InMemoryWorkspace();
        AtomicBoolean crash = new AtomicBoolean(true);

        Planner planner = task -> new Plan(List.of(
                new PlanStep(1, "alpha"),
                new PlanStep(2, "beta", List.of(1))));
        Supplier<Agent> worker = () -> request -> {
            if (request.input().startsWith("beta") && crash.get()) {
                throw new SimulatedCrash("boom"); // crash on the dependent step
            }
            return AgentResponse.completed("[" + request.input() + "]");
        };
        DeepAgent deep = DeepAgent.builder().planner(planner).worker(worker).synthesizer(SYNTH)
                .parallel(false).checkpoints(store).workspace(ws).build();

        assertThrows(SimulatedCrash.class, () -> deep.run(new AgentRequest("t", ctx("trace-dag"))));

        // Resume: step 1 is restored (DONE), and its result is injected into step 2's instruction.
        crash.set(false);
        deep.run(new AgentRequest("t", ctx("trace-dag")));

        String step2 = ws.read("step-2.txt").orElse("");
        assertTrue(step2.contains("Context from prior steps:"), step2);
        assertTrue(step2.contains("[alpha]"), step2); // step 1's restored result reached step 2
    }
}
