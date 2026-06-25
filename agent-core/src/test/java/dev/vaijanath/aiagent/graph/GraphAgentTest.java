package dev.vaijanath.aiagent.graph;

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

class GraphAgentTest {

    /** Not a RuntimeException, so the walk loop does not catch it — it propagates like a real crash. */
    private static final class Crash extends Error {
        Crash(String message) {
            super(message);
        }
    }

    private static RequestContext ctx(String traceId) {
        return new RequestContext("session", "principal", "acme", traceId, null, Map.of());
    }

    @Test
    void walksALinearGraph() {
        Agent a = req -> AgentResponse.completed(req.input() + "A");
        Agent b = req -> AgentResponse.completed(req.input() + "B");

        AgentResponse r = GraphAgent.builder()
                .node("a", a).node("b", b)
                .start("a").edge("a", "b").edge("b", GraphAgent.END)
                .build()
                .run(new AgentRequest("x:"));

        assertTrue(r.isCompleted());
        assertEquals("x:AB", r.output()); // state flows a -> b
    }

    @Test
    void supportsConditionalEdgesAndCycles() {
        Agent count = req -> AgentResponse.completed(req.input() + "x");

        AgentResponse r = GraphAgent.builder()
                .node("count", count)
                .start("count")
                .edge("count", state -> state.length() >= 3 ? GraphAgent.END : "count")
                .build()
                .run(new AgentRequest(""));

        assertEquals("xxx", r.output()); // looped back to 'count' until the state reached length 3
    }

    @Test
    void stopsAtTheStepBudget() {
        Agent loop = req -> AgentResponse.completed("loop");

        AgentResponse r = GraphAgent.builder()
                .node("n", loop).start("n").edge("n", "n") // an infinite cycle
                .maxSteps(5)
                .build()
                .run(new AgentRequest("go"));

        assertEquals("max_steps", r.stopReason());
    }

    @Test
    void aBlockedNodeShortCircuits() {
        Agent blocker = req -> AgentResponse.blocked("not allowed", "guardrail");

        AgentResponse r = GraphAgent.builder().node("n", blocker).start("n").build()
                .run(new AgentRequest("x"));

        assertTrue(r.blocked());
        assertEquals("not allowed", r.output());
    }

    @Test
    void anUnknownNextNodeEndsWithTheCurrentState() {
        Agent a = req -> AgentResponse.completed("done");

        AgentResponse r = GraphAgent.builder()
                .node("a", a).start("a").edge("a", state -> "ghost") // unknown target -> end
                .build()
                .run(new AgentRequest("x"));

        assertTrue(r.isCompleted());
        assertEquals("done", r.output());
    }

    @Test
    void resumesFromCheckpointAfterACrashRerunningOnlyTheCrashedNode() {
        CheckpointStore store = new InMemoryCheckpointStore();
        List<String> ran = new CopyOnWriteArrayList<>();
        AtomicBoolean crash = new AtomicBoolean(true);
        Agent a = req -> {
            ran.add("a");
            return AgentResponse.completed("A(" + req.input() + ")");
        };
        Agent b = req -> {
            if (crash.get()) {
                throw new Crash("boom");
            }
            ran.add("b");
            return AgentResponse.completed("B(" + req.input() + ")");
        };
        GraphAgent graph = GraphAgent.builder()
                .node("a", a).node("b", b)
                .start("a").edge("a", "b").edge("b", GraphAgent.END)
                .checkpoints(store)
                .build();

        // First walk: 'a' runs and is checkpointed, then 'b' crashes.
        assertThrows(Crash.class, () -> graph.run(new AgentRequest("x", ctx("t1"))));
        assertEquals(List.of("a"), ran);
        assertTrue(store.load("acme", "t1").isPresent());

        // Resume: pick up at 'b' with 'a's output as the state; 'a' is not re-run.
        crash.set(false);
        AgentResponse r = graph.run(new AgentRequest("x", ctx("t1")));

        assertEquals("B(A(x))", r.output());
        assertEquals(List.of("a", "b"), ran);
        assertTrue(store.load("acme", "t1").isEmpty()); // cleaned up after completion
    }

    @Test
    void rejectsNoNodesOrUnknownStart() {
        assertThrows(IllegalArgumentException.class, () -> GraphAgent.builder().build());
        assertThrows(IllegalArgumentException.class, () -> GraphAgent.builder()
                .node("a", req -> AgentResponse.completed(""))
                .start("ghost")
                .build());
    }
}
