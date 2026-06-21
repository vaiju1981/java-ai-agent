package dev.vaijanath.aiagent.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vaijanath.aiagent.guardrail.KeywordBlocklistGuardrail;
import dev.vaijanath.aiagent.model.ModelPort;
import dev.vaijanath.aiagent.model.StubModelPort;
import java.util.List;
import org.junit.jupiter.api.Test;

class DefaultAgentTest {

    @Test
    void stubModelEchoesAndCompletes() {
        Agent agent = DefaultAgent.builder()
                .model(new StubModelPort())
                .build();

        AgentResponse r = agent.run(new AgentRequest("hello there"));

        assertFalse(r.blocked());
        assertEquals("completed", r.stopReason());
        assertTrue(r.output().contains("hello there"));
    }

    @Test
    void inputGuardrailBlocksBeforeModel() {
        Agent agent = DefaultAgent.builder()
                .model(new StubModelPort())
                .guardrail(new KeywordBlocklistGuardrail(
                        List.of("forbidden"), "Let's talk about something else."))
                .build();

        AgentResponse r = agent.run(new AgentRequest("this is forbidden"));

        assertTrue(r.blocked());
        assertEquals("Let's talk about something else.", r.output());
    }

    @Test
    void modelFailureEndsGracefully() {
        ModelPort broken = request -> {
            throw new RuntimeException("model down");
        };

        AgentResponse r = DefaultAgent.builder().model(broken).build().run(new AgentRequest("hi"));

        assertFalse(r.blocked());
        assertEquals("model_error", r.stopReason());
    }
}
