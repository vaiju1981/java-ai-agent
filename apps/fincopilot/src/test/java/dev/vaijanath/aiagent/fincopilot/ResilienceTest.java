package dev.vaijanath.aiagent.fincopilot;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.vaijanath.aiagent.agent.Agent;
import dev.vaijanath.aiagent.agent.AgentRequest;
import dev.vaijanath.aiagent.agent.AgentResponse;
import dev.vaijanath.aiagent.agent.ProductionAgentRuntime;
import dev.vaijanath.aiagent.agent.RequestContext;
import dev.vaijanath.aiagent.audit.InMemoryAuditSink;
import dev.vaijanath.aiagent.memory.InMemoryConversationStore;
import dev.vaijanath.aiagent.model.Message;
import dev.vaijanath.aiagent.model.ModelPort;
import dev.vaijanath.aiagent.model.ModelResponse;
import dev.vaijanath.aiagent.model.Role;
import dev.vaijanath.aiagent.tools.jsonschema.JsonSchemaToolValidator;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/**
 * Resilience / chaos checks on the governed runtime FinCopilot relies on, using in-process fakes (no
 * model, no database): a failing model degrades to a graceful {@code model_error} response rather than
 * throwing to the caller, and concurrent turns stay isolated without deadlock.
 */
class ResilienceTest {

    private static Agent agent(ModelPort model) {
        return ProductionAgentRuntime.builder()
                .model(model)
                .conversationStore(new InMemoryConversationStore())
                .auditSink(new InMemoryAuditSink())
                .argumentValidator(new JsonSchemaToolValidator())
                // Generous timeouts: these tests probe isolation and graceful failure, not latency, so
                // no turn should trip a wall-clock bound even on a loaded CI runner.
                .modelTimeout(Duration.ofSeconds(60))
                .toolTimeout(Duration.ofSeconds(60))
                .build();
    }

    @Test
    void modelFailureDegradesGracefully() {
        Agent agent = agent(request -> {
            throw new RuntimeException("model down");
        });

        AgentResponse response = agent.run(new AgentRequest("hello"));

        assertEquals("model_error", response.stopReason(), "a model failure is a graceful stop, not an exception");
    }

    @Test
    void concurrentTurnsStayIsolated() throws Exception {
        // Echo the last user message; with distinct inputs per turn, any cross-talk would show up.
        Agent agent = agent(request -> {
            String last = "";
            for (Message m : request.messages()) {
                if (m.role() == Role.USER) {
                    last = m.content();
                }
            }
            return ModelResponse.text(last);
        });

        int turns = 50;
        ExecutorService pool = Executors.newFixedThreadPool(12);
        try {
            List<Future<String>> futures = new ArrayList<>();
            for (int i = 0; i < turns; i++) {
                int idx = i;
                futures.add(pool.submit(() -> agent.run(turn(idx)).output()));
            }
            for (int i = 0; i < turns; i++) {
                assertEquals("turn-" + i, futures.get(i).get(60, TimeUnit.SECONDS), "turn " + i + " stayed isolated");
            }
        } finally {
            pool.shutdownNow();
        }
    }

    private static AgentRequest turn(int i) {
        // No deadline (null): this test asserts session isolation, not deadline behaviour, so a turn must
        // not race a wall-clock bound under CI scheduling. Distinct session/principal/tenant per turn.
        return new AgentRequest(
                "turn-" + i, new RequestContext("s-" + i, "u-" + i, "u-" + i, "t-" + i, null, Map.of()));
    }
}
