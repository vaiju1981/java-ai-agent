package dev.vaijanath.aiagent.supervise;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vaijanath.aiagent.agent.Agent;
import dev.vaijanath.aiagent.agent.AgentRequest;
import dev.vaijanath.aiagent.agent.AgentResponse;
import dev.vaijanath.aiagent.agent.RequestContext;
import dev.vaijanath.aiagent.checkpoint.CheckpointStore;
import dev.vaijanath.aiagent.checkpoint.InMemoryCheckpointStore;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class ManagerAgentCheckpointTest {

    /** Not a RuntimeException, so it is not caught — it propagates out of run() like a real crash. */
    private static final class SimulatedCrash extends Error {
        SimulatedCrash(String message) {
            super(message);
        }
    }

    private static RequestContext ctx(String traceId) {
        return new RequestContext("session", "principal", "acme", traceId, null, Map.of());
    }

    @Test
    void resumesAfterACrashContinuingFromSavedHistory() {
        CheckpointStore store = new InMemoryCheckpointStore();
        List<String> ran = new CopyOnWriteArrayList<>();
        AtomicBoolean crash = new AtomicBoolean(true);

        // Delegate three times (step-1, step-2, step-3), then finish.
        Manager m = (task, history, roster) -> history.size() < 3
                ? Manager.Decision.delegate("w", "step-" + (history.size() + 1))
                : Manager.Decision.finish("done after " + history.size());
        Agent worker = request -> {
            if ("step-2".equals(request.input()) && crash.get()) {
                throw new SimulatedCrash("boom"); // crash partway through
            }
            ran.add(request.input());
            return AgentResponse.completed("out:" + request.input());
        };
        ManagerAgent agent = ManagerAgent.builder()
                .specialist("w", "worker", worker).manager(m).checkpoints(store).build();

        // First attempt records step-1, then crashes on step-2.
        assertThrows(SimulatedCrash.class, () -> agent.run(new AgentRequest("t", ctx("trace-1"))));
        assertEquals(List.of("step-1"), ran);
        assertTrue(store.load("acme", "trace-1").isPresent());

        // Retry with the same traceId resumes: step-1 is not re-run.
        crash.set(false);
        AgentResponse r = agent.run(new AgentRequest("t", ctx("trace-1")));

        assertEquals("done after 3", r.output());
        assertEquals(List.of("step-1", "step-2", "step-3"), ran);
        assertTrue(store.load("acme", "trace-1").isEmpty()); // cleaned up on finish
    }

    @Test
    void resumePreservesRoundContentIncludingTrickyCharacters() {
        CheckpointStore store = new InMemoryCheckpointStore();
        AtomicBoolean crash = new AtomicBoolean(true);
        String tricky = "do x: now\nthen y"; // a colon and a newline would break a naive delimiter

        Manager m = (task, history, roster) -> {
            if (history.isEmpty()) {
                return Manager.Decision.delegate("w", tricky);
            }
            if (history.size() == 1 && crash.get()) {
                return Manager.Decision.delegate("w", "boom");
            }
            return Manager.Decision.finish(
                    "round1 was [" + history.get(0).instruction() + "] => [" + history.get(0).output() + "]");
        };
        Agent worker = request -> {
            if ("boom".equals(request.input()) && crash.get()) {
                throw new SimulatedCrash("x");
            }
            return AgentResponse.completed("OUT(" + request.input() + ")");
        };
        ManagerAgent agent = ManagerAgent.builder()
                .specialist("w", "worker", worker).manager(m).checkpoints(store).build();

        assertThrows(SimulatedCrash.class, () -> agent.run(new AgentRequest("t", ctx("trace-2"))));
        crash.set(false);
        AgentResponse r = agent.run(new AgentRequest("t", ctx("trace-2")));

        assertTrue(r.output().contains("[" + tricky + "]"), r.output()); // instruction round-tripped
        assertTrue(r.output().contains("OUT(" + tricky + ")"), r.output()); // output round-tripped
    }

    @Test
    void deletesCheckpointWhenTheBudgetIsSpent() {
        CheckpointStore store = new InMemoryCheckpointStore();
        Manager alwaysDelegate = (task, history, roster) -> Manager.Decision.delegate("w", "again");
        Agent worker = request -> AgentResponse.completed("ok");

        AgentResponse r = ManagerAgent.builder().specialist("w", "worker", worker)
                .manager(alwaysDelegate).maxRounds(2).checkpoints(store).build()
                .run(new AgentRequest("t", ctx("trace-3")));

        assertEquals("max_rounds", r.stopReason());
        assertTrue(store.load("acme", "trace-3").isEmpty());
    }

    @Test
    void withoutAStoreNothingIsPersisted() {
        Manager m = (task, history, roster) -> Manager.Decision.finish("ok");
        Agent worker = request -> AgentResponse.completed("ignored");

        AgentResponse r = ManagerAgent.builder().specialist("w", "worker", worker).manager(m).build()
                .run(new AgentRequest("t", ctx("trace-4")));

        assertEquals("ok", r.output()); // unchanged behavior, no NPE without a store
    }
}
