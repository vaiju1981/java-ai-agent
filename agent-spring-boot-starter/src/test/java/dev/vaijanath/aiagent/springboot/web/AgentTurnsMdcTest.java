package dev.vaijanath.aiagent.springboot.web;

import static org.assertj.core.api.Assertions.assertThat;

import dev.vaijanath.aiagent.agent.Agent;
import dev.vaijanath.aiagent.agent.AgentRequest;
import dev.vaijanath.aiagent.agent.AgentResponse;
import dev.vaijanath.aiagent.agent.RequestContext;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

class AgentTurnsMdcTest {

    @Test
    void bindsRequestIdentityToMdcDuringTheTurnAndClearsAfter() {
        Map<String, String> seenDuringTurn = new HashMap<>();
        Agent agent = request -> {
            seenDuringTurn.put("traceId", MDC.get("traceId"));
            seenDuringTurn.put("sessionId", MDC.get("sessionId"));
            seenDuringTurn.put("principal", MDC.get("principal"));
            seenDuringTurn.put("tenant", MDC.get("tenant"));
            return AgentResponse.completed("ok");
        };
        RequestContext context = new RequestContext("sess-1", "alice", "acme", "trace-9", null, Map.of());

        AgentTurns.run(agent, new AgentRequest("hello", context));

        assertThat(seenDuringTurn)
                .containsEntry("traceId", "trace-9")
                .containsEntry("sessionId", "sess-1")
                .containsEntry("principal", "alice")
                .containsEntry("tenant", "acme");
        // Cleared after the turn so identity never leaks to the next task on a pooled thread.
        assertThat(MDC.get("traceId")).isNull();
        assertThat(MDC.get("principal")).isNull();
    }
}
