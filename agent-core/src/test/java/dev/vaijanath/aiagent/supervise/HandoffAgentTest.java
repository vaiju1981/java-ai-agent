package dev.vaijanath.aiagent.supervise;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vaijanath.aiagent.agent.Agent;
import dev.vaijanath.aiagent.agent.AgentRequest;
import dev.vaijanath.aiagent.agent.AgentResponse;
import org.junit.jupiter.api.Test;

class HandoffAgentTest {

    private static final Agent TRIAGE = req -> AgentResponse.completed("triage saw: " + req.input());
    private static final Agent BILLING = req -> AgentResponse.completed("billing handled it");

    private static HandoffAgent.Builder team(Handoff handoff) {
        return HandoffAgent.builder()
                .agent("triage", "routes incoming requests", TRIAGE)
                .agent("billing", "billing, invoices, refunds", BILLING)
                .start("triage")
                .handoff(handoff);
    }

    @Test
    void transfersControlToAPeerThenStops() {
        // triage hands off to billing; billing keeps control (its output is the answer)
        Handoff h = (task, current, lastOutput, roster) -> current.equals("triage") ? "billing" : current;

        AgentResponse r = team(h).build().run(new AgentRequest("refund please"));

        assertTrue(r.isCompleted());
        assertEquals("billing handled it", r.output());
    }

    @Test
    void keepsControlWhenThereIsNoHandoff() {
        Handoff stay = (task, current, lastOutput, roster) -> current;

        AgentResponse r = team(stay).build().run(new AgentRequest("hello"));

        assertEquals("triage saw: hello", r.output());
    }

    @Test
    void stopsAtTheHopBudgetWhenPeersPingPong() {
        Handoff pingPong =
                (task, current, lastOutput, roster) -> current.equals("triage") ? "billing" : "triage";

        AgentResponse r = team(pingPong).maxHops(4).build().run(new AgentRequest("loop"));

        assertEquals("max_hops", r.stopReason());
    }

    @Test
    void unknownPeerStopsWithTheCurrentOutput() {
        Handoff toGhost = (task, current, lastOutput, roster) -> "ghost";

        AgentResponse r = team(toGhost).build().run(new AgentRequest("x"));

        assertEquals("triage saw: x", r.output()); // unknown target -> stop, keep current's output
    }

    @Test
    void aBlockedHopShortCircuits() {
        Agent blocker = req -> AgentResponse.blocked("not allowed", "guardrail");

        AgentResponse r = HandoffAgent.builder()
                .agent("b", "blocks", blocker)
                .start("b")
                .handoff((task, current, lastOutput, roster) -> current)
                .build()
                .run(new AgentRequest("x"));

        assertTrue(r.blocked());
        assertEquals("not allowed", r.output());
    }

    @Test
    void rejectsNoAgentsOrUnknownStart() {
        assertThrows(IllegalArgumentException.class,
                () -> HandoffAgent.builder().handoff((t, c, o, r) -> c).build());
        assertThrows(IllegalArgumentException.class,
                () -> team((t, c, o, r) -> c).start("ghost").build());
    }
}
