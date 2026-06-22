package dev.vaijanath.aiagent.springboot.web;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.vaijanath.aiagent.agent.Agent;
import dev.vaijanath.aiagent.agent.AgentRequest;
import dev.vaijanath.aiagent.agent.AgentResponse;
import dev.vaijanath.aiagent.agent.RequestContext;
import dev.vaijanath.aiagent.observe.AgentObserver;
import java.time.Instant;
import java.util.Map;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class AgentTurnsTest {

    private static AgentRequest request(String input) {
        return new AgentRequest(
                input, new RequestContext("s1", "p1", "t1", "trace-1", Instant.now().plusSeconds(60), Map.of()));
    }

    @Test
    void requireIdentifierAcceptsValidAndRejectsInvalid() {
        AgentTurns.requireIdentifier("user-1", "principal"); // valid: no throw
        assertThrows(ResponseStatusException.class, () -> AgentTurns.requireIdentifier(null, "principal"));
        assertThrows(ResponseStatusException.class, () -> AgentTurns.requireIdentifier("has space", "principal"));
    }

    @Test
    void requireInputBoundsLength() {
        assertEquals("hello", AgentTurns.requireInput("hello", 100));
        assertThrows(ResponseStatusException.class, () -> AgentTurns.requireInput("   ", 100));
        assertThrows(ResponseStatusException.class, () -> AgentTurns.requireInput(null, 100));
        assertThrows(ResponseStatusException.class, () -> AgentTurns.requireInput("toolong", 3));
    }

    @Test
    void httpStatusMapsOutcomes() {
        assertEquals(
                HttpStatus.GATEWAY_TIMEOUT,
                AgentTurns.httpStatus(AgentResponse.stopped("x", "deadline_exceeded")));
        assertEquals(
                HttpStatus.SERVICE_UNAVAILABLE,
                AgentTurns.httpStatus(AgentResponse.stopped("x", "model_error")));
        assertEquals(HttpStatus.OK, AgentTurns.httpStatus(AgentResponse.completed("x")));
    }

    @Test
    void runReturnsStatusAndBody() {
        Agent ok = req -> AgentResponse.completed("ok");
        var response = AgentTurns.run(ok, request("hi"));
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("ok", response.getBody().output());

        Agent down = req -> AgentResponse.stopped("down", "model_error");
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, AgentTurns.run(down, request("hi")).getStatusCode());
    }

    @Test
    void streamRunsTurnWithoutThrowing() {
        Function<AgentObserver, Agent> factory = observer -> req -> {
            observer.onToolCall(new dev.vaijanath.aiagent.model.ToolCall("c1", "lookup", "{}"));
            return AgentResponse.completed("streamed");
        };
        assertDoesNotThrow(() -> AgentTurns.stream(factory, request("hi"), Runnable::run, 1_000L));
    }
}
