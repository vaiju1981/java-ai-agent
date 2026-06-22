package dev.vaijanath.aiagent.reference;

import dev.vaijanath.aiagent.agent.Agent;
import dev.vaijanath.aiagent.agent.AgentRequest;
import dev.vaijanath.aiagent.agent.AgentResponse;
import dev.vaijanath.aiagent.agent.RequestContext;
import dev.vaijanath.aiagent.model.ToolCall;
import dev.vaijanath.aiagent.observe.AgentObserver;
import dev.vaijanath.aiagent.store.jdbc.ConcurrentConversationException;
import dev.vaijanath.aiagent.tool.ToolResult;
import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/agent")
class AgentController {

    private static final Logger log = LoggerFactory.getLogger(AgentController.class);
    private static final int MAX_INPUT_CHARS = 32_000;

    private final Agent agent;
    private final Function<AgentObserver, Agent> streamingAgentFactory;
    private final Executor streamExecutor;
    private final AgentProperties properties;

    AgentController(
            Agent agent,
            Function<AgentObserver, Agent> streamingAgentFactory,
            Executor streamExecutor,
            AgentProperties properties) {
        this.agent = agent;
        this.streamingAgentFactory = streamingAgentFactory;
        this.streamExecutor = streamExecutor;
        this.properties = properties;
    }

    @PostMapping("/turn")
    ResponseEntity<AgentResponse> turn(
            @RequestBody(required = false) TurnRequest body,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenant,
            @RequestHeader(value = "X-Principal-Id", required = false) String principal,
            @RequestHeader(value = "X-Trace-Id", required = false) String traceId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        Prepared prepared = prepare(body, tenant, principal, traceId, idempotencyKey);
        MDC.put("traceId", prepared.trace());
        try {
            AgentResponse response = agent.run(new AgentRequest(prepared.input(), prepared.context()));
            return ResponseEntity.status(statusFor(response)).body(response);
        } finally {
            MDC.remove("traceId");
        }
    }

    /**
     * Streams a turn as Server-Sent Events: a {@code tool} event per tool call and a {@code tool_result}
     * event (name + ok flag only) per result, then a single {@code final} event carrying the guarded
     * {@link AgentResponse}.
     *
     * <p>Raw model tokens are deliberately <b>not</b> streamed: output guardrails run on the final
     * answer, so emitting pre-guardrail tokens would bypass the safety layer. Clients get real-time
     * tool activity plus the guarded result.
     */
    @PostMapping(value = "/turn/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    SseEmitter stream(
            @RequestBody(required = false) TurnRequest body,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenant,
            @RequestHeader(value = "X-Principal-Id", required = false) String principal,
            @RequestHeader(value = "X-Trace-Id", required = false) String traceId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        Prepared prepared = prepare(body, tenant, principal, traceId, idempotencyKey);
        SseEmitter emitter = new SseEmitter(properties.requestTimeout().toMillis() + 5_000L);
        Agent streamingAgent = streamingAgentFactory.apply(new SseObserver(emitter));
        streamExecutor.execute(() -> {
            MDC.put("traceId", prepared.trace());
            try {
                AgentResponse response =
                        streamingAgent.run(new AgentRequest(prepared.input(), prepared.context()));
                emitter.send(SseEmitter.event().name("final").data(response, MediaType.APPLICATION_JSON));
                emitter.complete();
            } catch (Exception e) {
                log.warn("streaming turn failed", e);
                try {
                    emitter.send(SseEmitter.event().name("error").data(Map.of("error", "turn failed")));
                } catch (IOException | RuntimeException ignored) {
                    // client already gone; nothing more to send
                }
                emitter.complete();
            } finally {
                MDC.remove("traceId");
            }
        });
        return emitter;
    }

    /** Validates the request and builds the {@link RequestContext}; shared by both endpoints. */
    private Prepared prepare(
            TurnRequest body, String tenant, String principal, String traceId, String idempotencyKey) {
        String input = body == null ? null : body.input();
        String session = body == null ? null : body.sessionId();
        requireIdentifier(tenant, "tenant");
        requireIdentifier(principal, "principal");
        requireIdentifier(session, "sessionId");
        if (traceId != null && !traceId.isBlank()) {
            requireIdentifier(traceId, "traceId");
        }
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            requireIdentifier(idempotencyKey, "idempotencyKey");
        }
        if (input == null || input.isBlank() || input.length() > MAX_INPUT_CHARS) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "input must contain 1-" + MAX_INPUT_CHARS + " characters");
        }
        String trace = traceId == null || traceId.isBlank() ? UUID.randomUUID().toString() : traceId;
        RequestContext context = new RequestContext(
                session,
                principal,
                tenant,
                trace,
                Instant.now().plus(properties.requestTimeout()),
                idempotencyKey == null || idempotencyKey.isBlank()
                        ? Map.of()
                        : Map.of("idempotencyKey", idempotencyKey));
        return new Prepared(input, trace, context);
    }

    /**
     * Maps the turn outcome to an HTTP status: a model outage is 503 and a deadline is 504 (so load
     * balancers and clients react correctly), while a completion, a step-budget stop, or a guardrail
     * block all return 200 with a valid body the client inspects.
     */
    private static HttpStatus statusFor(AgentResponse response) {
        return switch (response.stopReason()) {
            case "deadline_exceeded" -> HttpStatus.GATEWAY_TIMEOUT;
            case "model_error" -> HttpStatus.SERVICE_UNAVAILABLE;
            default -> HttpStatus.OK;
        };
    }

    private static void requireIdentifier(String value, String name) {
        if (value == null || !value.matches("[A-Za-z0-9._:-]{1,128}")) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, name + " must match [A-Za-z0-9._:-]{1,128}");
        }
    }

    @ExceptionHandler(ConcurrentConversationException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    Map<String, String> conversationConflict() {
        return Map.of("error", "conversation changed concurrently; retry the complete turn");
    }

    /** Forwards turn lifecycle to one SSE client: tool calls and result outcomes (never raw content). */
    private static final class SseObserver implements AgentObserver {

        private final SseEmitter emitter;

        SseObserver(SseEmitter emitter) {
            this.emitter = emitter;
        }

        @Override
        public void onToolCall(ToolCall call) {
            send("tool", Map.of("name", call.name()));
        }

        @Override
        public void onToolResult(String toolName, ToolResult result) {
            send("tool_result", Map.of("name", toolName, "ok", !result.error()));
        }

        private void send(String event, Object data) {
            try {
                emitter.send(SseEmitter.event().name(event).data(data, MediaType.APPLICATION_JSON));
            } catch (IOException | RuntimeException e) {
                // The client disconnected or the stream is already complete — drop the event.
            }
        }
    }

    record TurnRequest(String sessionId, String input) {}

    private record Prepared(String input, String trace, RequestContext context) {}
}
