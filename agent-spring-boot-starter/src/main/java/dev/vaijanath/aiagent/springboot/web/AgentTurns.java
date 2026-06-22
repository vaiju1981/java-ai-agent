package dev.vaijanath.aiagent.springboot.web;

import dev.vaijanath.aiagent.agent.Agent;
import dev.vaijanath.aiagent.agent.AgentRequest;
import dev.vaijanath.aiagent.agent.AgentResponse;
import dev.vaijanath.aiagent.observe.AgentObserver;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Shared HTTP plumbing for agent turn endpoints, so every agent web app applies the same contract:
 * identifier/input validation (rejecting bad input with {@code 400}), the turn-outcome to HTTP-status
 * mapping, the synchronous turn, and the streaming (SSE) turn. Controllers stay thin — they build the
 * {@link AgentRequest} (their own auth/context rules) and delegate execution here.
 */
public final class AgentTurns {

    private static final Logger LOG = LoggerFactory.getLogger(AgentTurns.class);
    private static final String ID_PATTERN = "[A-Za-z0-9._:-]{1,128}";

    private AgentTurns() {}

    /** Rejects an identifier that is null or does not match {@code [A-Za-z0-9._:-]{1,128}}. */
    public static void requireIdentifier(String value, String name) {
        if (value == null || !value.matches(ID_PATTERN)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, name + " must match [A-Za-z0-9._:-]{1,128}");
        }
    }

    /** Returns the input if it holds 1..{@code maxChars} non-blank characters, else rejects it. */
    public static String requireInput(String input, int maxChars) {
        if (input == null || input.isBlank() || input.length() > maxChars) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "input must contain 1-" + maxChars + " characters");
        }
        return input;
    }

    /**
     * Maps a turn outcome to an HTTP status: a model outage is 503 and a deadline is 504 (so load
     * balancers and clients react), while a completion, step-budget stop, or guardrail block return 200
     * with a valid body the client inspects.
     */
    public static HttpStatus httpStatus(AgentResponse response) {
        return switch (response.stopReason()) {
            case "deadline_exceeded" -> HttpStatus.GATEWAY_TIMEOUT;
            case "model_error" -> HttpStatus.SERVICE_UNAVAILABLE;
            default -> HttpStatus.OK;
        };
    }

    /** Runs one synchronous turn, correlating logs by trace id and mapping the outcome to a status. */
    public static ResponseEntity<AgentResponse> run(Agent agent, AgentRequest request) {
        MDC.put("traceId", request.context().traceId());
        try {
            AgentResponse response = agent.run(request);
            return ResponseEntity.status(httpStatus(response)).body(response);
        } finally {
            MDC.remove("traceId");
        }
    }

    /**
     * Streams one turn as Server-Sent Events off the request thread: a per-request {@link SseAgentObserver}
     * emits {@code tool}/{@code tool_result} events, then a single guarded {@code final} event (or an
     * {@code error} event on failure). Raw model tokens are never streamed.
     */
    public static SseEmitter stream(
            Function<AgentObserver, Agent> streamingAgentFactory,
            AgentRequest request,
            Executor executor,
            long emitterTimeoutMillis) {
        SseEmitter emitter = new SseEmitter(emitterTimeoutMillis);
        Agent agent = streamingAgentFactory.apply(new SseAgentObserver(emitter));
        executor.execute(() -> {
            MDC.put("traceId", request.context().traceId());
            try {
                AgentResponse response = agent.run(request);
                emitter.send(SseEmitter.event().name("final").data(response, MediaType.APPLICATION_JSON));
                emitter.complete();
            } catch (Exception e) {
                LOG.warn("streaming turn failed", e);
                try {
                    emitter.send(SseEmitter.event().name("error").data(Map.of("error", "turn failed")));
                } catch (IOException | RuntimeException ignored) {
                    // the client already disconnected — nothing more to send
                }
                emitter.complete();
            } finally {
                MDC.remove("traceId");
            }
        });
        return emitter;
    }
}
