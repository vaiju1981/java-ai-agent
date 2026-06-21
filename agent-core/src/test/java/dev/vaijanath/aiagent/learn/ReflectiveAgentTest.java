package dev.vaijanath.aiagent.learn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vaijanath.aiagent.agent.Agent;
import dev.vaijanath.aiagent.agent.AgentRequest;
import dev.vaijanath.aiagent.agent.AgentResponse;
import dev.vaijanath.aiagent.agent.RequestContext;
import dev.vaijanath.aiagent.memory.InMemoryEpisodicStore;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

class ReflectiveAgentTest {

    // A worker that gets it wrong until the correct fact ("Canberra") is present in its input.
    private static final Supplier<Agent> WORKER = () -> request ->
            request.input().contains("Canberra")
                    ? AgentResponse.completed("The capital of Australia is Canberra.")
                    : AgentResponse.completed("The capital of Australia is Sydney.");

    // A reviewer that rejects the common mistake and teaches the lesson.
    private static final Reflector REFLECTOR = (task, answer) ->
            answer.contains("Canberra")
                    ? Reflection.ok()
                    : Reflection.issue("The capital of Australia is Canberra, not Sydney.");

    @Test
    void retriesWithLessonWithinASingleRun() {
        InMemoryEpisodicStore store = new InMemoryEpisodicStore();
        ReflectiveAgent agent = ReflectiveAgent.builder()
                .worker(WORKER).reflector(REFLECTOR).memory(store).maxAttempts(2)
                .build();

        AgentResponse r = agent.run(new AgentRequest("What is the capital of Australia?"));

        assertTrue(r.output().contains("Canberra"), "should self-correct; got: " + r.output());
        // The failed first attempt was recorded as a lesson.
        assertEquals(1, store.recall("capital of Australia", 5).size());
    }

    @Test
    void appliesAPastLessonAcrossRuns() {
        InMemoryEpisodicStore store = new InMemoryEpisodicStore();

        // Run 1: allowed to retry, so it learns and records the lesson.
        ReflectiveAgent.builder().worker(WORKER).reflector(REFLECTOR).memory(store).maxAttempts(2)
                .build()
                .run(new AgentRequest("What is the capital of Australia?"));

        // Run 2: NO retries allowed (maxAttempts=1) but the same store — recall must inject the
        // lesson up front so it gets it right on the first try.
        AgentResponse second = ReflectiveAgent.builder()
                .worker(WORKER).reflector(REFLECTOR).memory(store).maxAttempts(1)
                .build()
                .run(new AgentRequest("Tell me the capital of Australia."));

        assertTrue(second.output().contains("Canberra"),
                "cross-run learning should fix it on the first attempt; got: " + second.output());
    }

    @Test
    void retriesCarryTheCallersGovernanceContext() {
        List<String> tenantsSeen = new CopyOnWriteArrayList<>();
        Supplier<Agent> worker = () -> request -> {
            tenantsSeen.add(request.context().tenant());
            return AgentResponse.completed("ok");
        };
        ReflectiveAgent agent = ReflectiveAgent.builder()
                .worker(worker).reflector((task, answer) -> Reflection.ok()).maxAttempts(1)
                .build();

        agent.run(new AgentRequest("hi", new RequestContext("s", "alice", "acme", null, null, null)));

        assertTrue(tenantsSeen.contains("acme"),
                "the worker must inherit the caller's tenant, not an anonymous context: " + tenantsSeen);
    }
}
