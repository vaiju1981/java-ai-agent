package dev.vaijanath.aiagent.a2a;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import dev.vaijanath.aiagent.agent.AgentResponse;
import org.junit.jupiter.api.Test;

class A2aMessagesTest {

    @Test
    void requestOfCarriesOnlyInput() {
        A2aRequest r = A2aRequest.of("hi");

        assertEquals("hi", r.input());
        assertNull(r.sessionId());
        assertNull(r.principal());
        assertNull(r.tenant());
        assertNull(r.traceId());
        assertNull(r.deadlineEpochMillis());
    }

    @Test
    void responseRoundTripsEachOutcome() {
        AgentResponse[] outcomes = {
            AgentResponse.completed("done"),
            AgentResponse.blocked("safe", "guardrail"),
            AgentResponse.stopped("partial", "max_steps"),
        };
        for (AgentResponse original : outcomes) {
            assertEquals(original, A2aResponse.from(original).toAgentResponse());
        }
    }
}
