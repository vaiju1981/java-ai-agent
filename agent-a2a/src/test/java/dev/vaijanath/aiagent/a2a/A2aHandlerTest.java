package dev.vaijanath.aiagent.a2a;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.vaijanath.aiagent.agent.Agent;
import dev.vaijanath.aiagent.agent.AgentResponse;
import dev.vaijanath.aiagent.agent.RequestContext;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class A2aHandlerTest {

    private final ObjectMapper json = new ObjectMapper();

    @Test
    void runsATurnAndReturnsTheResponse() throws Exception {
        A2aHandler handler =
                new A2aHandler(req -> AgentResponse.completed("echo:" + req.input()), "bot", "d");

        A2aResponse r = json.readValue(handler.handle("{\"input\":\"hi\"}"), A2aResponse.class);

        assertEquals("echo:hi", r.output());
        assertFalse(r.blocked());
        assertEquals("completed", r.stopReason());
    }

    @Test
    void propagatesIdentityToTheAgent() {
        AtomicReference<RequestContext> seen = new AtomicReference<>();
        Agent capturing = req -> {
            seen.set(req.context());
            return AgentResponse.completed("ok");
        };

        new A2aHandler(capturing, "bot", "d")
                .handle("{\"input\":\"x\",\"sessionId\":\"s1\",\"principal\":\"alice\","
                        + "\"tenant\":\"acme\",\"traceId\":\"t1\",\"deadlineEpochMillis\":1900000000000}");

        assertEquals("s1", seen.get().sessionId());
        assertEquals("alice", seen.get().principal());
        assertEquals("acme", seen.get().tenant());
        assertEquals("t1", seen.get().traceId());
        assertTrue(seen.get().deadlineAt().isPresent(), "the deadline survives the hop");
        assertEquals(1900000000000L, seen.get().deadlineAt().get().toEpochMilli());
    }

    @Test
    void mapsABlockedResult() throws Exception {
        A2aHandler handler =
                new A2aHandler(req -> AgentResponse.blocked("nope", "guardrail"), "bot", "d");

        A2aResponse r = json.readValue(handler.handle("{\"input\":\"x\"}"), A2aResponse.class);

        assertTrue(r.blocked());
        assertEquals("guardrail", r.stopReason());
        assertEquals("nope", r.output());
    }

    @Test
    void rejectsMissingInputOrMalformedJson() {
        A2aHandler handler = new A2aHandler(req -> AgentResponse.completed("x"), "bot", "d");

        assertThrows(IllegalArgumentException.class, () -> handler.handle("{}"));
        assertThrows(IllegalArgumentException.class, () -> handler.handle("not json"));
    }

    @Test
    void rendersTheAgentCard() throws Exception {
        A2aHandler handler =
                new A2aHandler(req -> AgentResponse.completed("x"), "billing", "handles invoices");

        AgentCard card = json.readValue(handler.cardJson(), AgentCard.class);

        assertEquals("billing", card.name());
        assertEquals("handles invoices", card.description());
    }

    @Test
    void ignoresUnknownWireFieldsForForwardCompatibility() throws Exception {
        A2aHandler handler =
                new A2aHandler(req -> AgentResponse.completed("ok:" + req.input()), "bot", "d");

        A2aResponse r =
                json.readValue(handler.handle("{\"input\":\"hi\",\"future\":\"x\"}"), A2aResponse.class);

        assertEquals("ok:hi", r.output());
    }
}
