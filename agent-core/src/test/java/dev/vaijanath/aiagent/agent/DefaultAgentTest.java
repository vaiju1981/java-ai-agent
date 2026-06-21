package dev.vaijanath.aiagent.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vaijanath.aiagent.guardrail.KeywordBlocklistGuardrail;
import dev.vaijanath.aiagent.model.ModelPort;
import dev.vaijanath.aiagent.model.ModelResponse;
import dev.vaijanath.aiagent.model.Role;
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

    /** Memory is shared within a session and isolated across sessions on one shared agent. */
    @Test
    void sessionMemoryIsSharedWithinAndIsolatedAcross() {
        // Echoes how many user turns the model can see in the history.
        ModelPort countingUserTurns = request -> ModelResponse.text(Long.toString(
                request.messages().stream().filter(m -> m.role() == Role.USER).count()));
        Agent agent = DefaultAgent.builder().model(countingUserTurns).build();

        RequestContext s1 = RequestContext.session("s1");
        assertEquals("1", agent.run(new AgentRequest("first", s1)).output());
        assertEquals("2", agent.run(new AgentRequest("second", s1)).output(), "same session remembers");

        RequestContext s2 = RequestContext.session("s2");
        assertEquals("1", agent.run(new AgentRequest("hello", s2)).output(), "other session is isolated");

        // The no-context convenience constructor is a fresh session every time.
        assertEquals("1", agent.run(new AgentRequest("a")).output());
        assertEquals("1", agent.run(new AgentRequest("b")).output());
    }

    @Test
    void sameSessionIdAcrossTenantsIsIsolated() {
        ModelPort countsUserTurns = request -> ModelResponse.text(Long.toString(
                request.messages().stream().filter(m -> m.role() == Role.USER).count()));
        Agent agent = DefaultAgent.builder().model(countsUserTurns).build();

        RequestContext tenantA = new RequestContext("shared", null, "tenant-a", null, null, null);
        RequestContext tenantB = new RequestContext("shared", null, "tenant-b", null, null, null);

        assertEquals("1", agent.run(new AgentRequest("a1", tenantA)).output());
        assertEquals("1", agent.run(new AgentRequest("b1", tenantB)).output(),
                "the same sessionId under a different tenant must not share memory");
        assertEquals("2", agent.run(new AgentRequest("a2", tenantA)).output(),
                "the same (tenant, session) continues the conversation");
    }

    @Test
    void stopsWhenTheDeadlineHasPassed() {
        Agent agent = DefaultAgent.builder().model(new StubModelPort()).build();
        RequestContext past = new RequestContext(
                "s", null, null, null, java.time.Instant.now().minusSeconds(1), null);

        AgentResponse r = agent.run(new AgentRequest("hi", past));

        assertFalse(r.blocked());
        assertEquals("deadline_exceeded", r.stopReason());
    }
}
