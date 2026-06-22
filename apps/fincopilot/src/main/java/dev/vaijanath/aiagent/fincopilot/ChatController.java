package dev.vaijanath.aiagent.fincopilot;

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

/**
 * The chat surface. M0 takes the principal/session from request headers (real consumer auth replaces
 * this in the auth slice); each turn runs through the governed FinCopilot agent. Two endpoints: a sync
 * {@code /turn} and a streaming {@code /stream} that emits tool events then a single guarded {@code final}.
 *
 * <p>Raw model tokens are deliberately not streamed: output guardrails run on the final answer, so
 * emitting pre-guardrail tokens would bypass the safety layer.
 */
@RestController
@RequestMapping("/api/chat")
class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);
    private static final int MAX_INPUT_CHARS = 32_000;

    private final Agent agent;
    private final Function<AgentObserver, Agent> streamingAgentFactory;
    private final Executor streamExecutor;
    private final FinCopilotProperties properties;

    ChatController(
            Agent agent,
            Function<AgentObserver, Agent> streamingAgentFactory,
            Executor streamExecutor,
            FinCopilotProperties properties) {
        this.agent = agent;
        this.streamingAgentFactory = streamingAgentFactory;
        this.streamExecutor = streamExecutor;
        this.properties = properties;
    }

    @PostMapping("/turn")
    ResponseEntity<AgentResponse> turn(
            @RequestBody(required = false) TurnRequest body,
            @RequestHeader(value = "X-Principal-Id", required = false) String principal) {
        Prepared prepared = prepare(body, principal);
        MDC.put("traceId", prepared.context().traceId());
        try {
            AgentResponse response = agent.run(new AgentRequest(prepared.input(), prepared.context()));
            return ResponseEntity.status(statusFor(response)).body(response);
        } finally {
            MDC.remove("traceId");
        }
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    SseEmitter stream(
            @RequestBody(required = false) TurnRequest body,
            @RequestHeader(value = "X-Principal-Id", required = false) String principal) {
        Prepared prepared = prepare(body, principal);
        SseEmitter emitter = new SseEmitter(properties.requestTimeout().toMillis() + 5_000L);
        Agent streamingAgent = streamingAgentFactory.apply(new SseObserver(emitter));
        streamExecutor.execute(() -> {
            MDC.put("traceId", prepared.context().traceId());
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

    private Prepared prepare(TurnRequest body, String principal) {
        String input = body == null ? null : body.input();
        String session = body == null ? null : body.sessionId();
        String who = principal == null || principal.isBlank() ? "anonymous" : principal;
        requireIdentifier(who, "principal");
        requireIdentifier(session, "sessionId");
        if (input == null || input.isBlank() || input.length() > MAX_INPUT_CHARS) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "input must contain 1-" + MAX_INPUT_CHARS + " characters");
        }
        String trace = UUID.randomUUID().toString();
        RequestContext context = new RequestContext(
                session, who, who, trace, Instant.now().plus(properties.requestTimeout()), Map.of());
        return new Prepared(input, context);
    }

    /** Model outage -> 503 and a deadline -> 504 (so LBs/clients react); other outcomes are 200. */
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
                // client disconnected or stream already complete — drop the event
            }
        }
    }

    record TurnRequest(String sessionId, String input) {}

    private record Prepared(String input, RequestContext context) {}
}
