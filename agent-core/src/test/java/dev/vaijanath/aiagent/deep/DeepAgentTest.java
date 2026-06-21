package dev.vaijanath.aiagent.deep;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vaijanath.aiagent.agent.Agent;
import dev.vaijanath.aiagent.agent.AgentRequest;
import dev.vaijanath.aiagent.agent.AgentResponse;
import dev.vaijanath.aiagent.model.ModelPort;
import dev.vaijanath.aiagent.model.ModelResponse;
import java.time.Duration;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class DeepAgentTest {

    @Test
    void planFanOutThenSynthesize() {
        Planner planner = task -> new Plan(List.of(new PlanStep(1, "A"), new PlanStep(2, "B")));
        Supplier<Agent> worker = () -> request -> AgentResponse.completed("did:" + request.input());
        ModelPort synthesizer = request -> ModelResponse.text("FINAL");

        InMemoryWorkspace workspace = new InMemoryWorkspace();
        DeepAgent deep = DeepAgent.builder()
                .planner(planner).worker(worker).synthesizer(synthesizer).workspace(workspace)
                .build();

        AgentResponse r = deep.run(new AgentRequest("big task"));

        assertEquals("FINAL", r.output());
        assertEquals("did:A", workspace.read("step-1.txt").orElse(""));
        assertEquals("did:B", workspace.read("step-2.txt").orElse(""));
        assertTrue(workspace.read("plan.md").orElse("").contains("DONE"));
    }

    @Test
    void subtasksRunConcurrently() {
        int n = 4;
        AtomicInteger active = new AtomicInteger();
        AtomicInteger peak = new AtomicInteger();
        Supplier<Agent> worker = () -> request -> {
            int now = active.incrementAndGet();
            peak.accumulateAndGet(now, Math::max);
            try {
                Thread.sleep(60);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            active.decrementAndGet();
            return AgentResponse.completed("ok");
        };
        Planner planner = task ->
                new Plan(IntStream.rangeClosed(1, n).mapToObj(i -> new PlanStep(i, "s" + i)).toList());
        ModelPort synthesizer = request -> ModelResponse.text("done");

        DeepAgent deep = DeepAgent.builder()
                .planner(planner).worker(worker).synthesizer(synthesizer)
                .parallel(true).stepTimeout(Duration.ofSeconds(5))
                .build();

        AgentResponse r = deep.run(new AgentRequest("t"));

        assertEquals("done", r.output());
        assertTrue(peak.get() >= 2, "expected concurrent subtasks, peak=" + peak.get());
    }
}
