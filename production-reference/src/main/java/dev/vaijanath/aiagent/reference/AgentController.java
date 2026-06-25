package dev.vaijanath.aiagent.reference;

import dev.vaijanath.aiagent.agent.Agent;
import dev.vaijanath.aiagent.agent.AgentRequest;
import dev.vaijanath.aiagent.agent.AgentResponse;
import dev.vaijanath.aiagent.agent.RequestContext;
import dev.vaijanath.aiagent.observe.AgentObserver;
import dev.vaijanath.aiagent.springboot.web.AgentTurns;
import dev.vaijanath.aiagent.store.jdbc.ConcurrentConversationException;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.function.Function;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * The agent HTTP surface. The turn plumbing (validation, status mapping, sync + SSE execution) lives in
 * {@link AgentTurns}; this controller's job is the reference service's request shaping — tenant +
 * principal + session identity, an optional caller trace id, and idempotency-key passthrough.
 *
 * <p>Raw model tokens are deliberately not streamed: output guardrails run on the final answer.
 */
@RestController
@RequestMapping("/api/agent")
class AgentController {

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
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestAttribute(name = ApiKeyAuthenticationFilter.TENANT_ATTRIBUTE, required = false)
                    String authTenant) {
        return AgentTurns.run(agent, prepare(body, tenant, principal, traceId, idempotencyKey, authTenant));
    }

    @PostMapping(value = "/turn/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    SseEmitter stream(
            @RequestBody(required = false) TurnRequest body,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenant,
            @RequestHeader(value = "X-Principal-Id", required = false) String principal,
            @RequestHeader(value = "X-Trace-Id", required = false) String traceId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestAttribute(name = ApiKeyAuthenticationFilter.TENANT_ATTRIBUTE, required = false)
                    String authTenant) {
        AgentRequest request = prepare(body, tenant, principal, traceId, idempotencyKey, authTenant);
        return AgentTurns.stream(
                streamingAgentFactory, request, streamExecutor, properties.requestTimeout().toMillis() + 5_000L);
    }

    /** Validates the request and builds the {@link RequestContext}; shared by both endpoints. */
    private AgentRequest prepare(
            TurnRequest body, String tenant, String principal, String traceId, String idempotencyKey,
            String authTenant) {
        String input = body == null ? null : body.input();
        String session = body == null ? null : body.sessionId();
        // When auth is enabled the tenant is bound to the API key (set by the filter), so it is trusted
        // over any client-supplied X-Tenant-Id. With auth disabled (dev), fall back to the header.
        String effectiveTenant = authTenant != null && !authTenant.isBlank() ? authTenant : tenant;
        AgentTurns.requireIdentifier(effectiveTenant, "tenant");
        AgentTurns.requireIdentifier(principal, "principal");
        AgentTurns.requireIdentifier(session, "sessionId");
        if (traceId != null && !traceId.isBlank()) {
            AgentTurns.requireIdentifier(traceId, "traceId");
        }
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            AgentTurns.requireIdentifier(idempotencyKey, "idempotencyKey");
        }
        AgentTurns.requireInput(input, MAX_INPUT_CHARS);
        String trace = traceId == null || traceId.isBlank() ? UUID.randomUUID().toString() : traceId;
        RequestContext context = new RequestContext(
                session,
                principal,
                effectiveTenant,
                trace,
                Instant.now().plus(properties.requestTimeout()),
                idempotencyKey == null || idempotencyKey.isBlank()
                        ? Map.of()
                        : Map.of("idempotencyKey", idempotencyKey));
        return new AgentRequest(input, context);
    }

    @ExceptionHandler(ConcurrentConversationException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    Map<String, String> conversationConflict() {
        return Map.of("error", "conversation changed concurrently; retry the complete turn");
    }

    record TurnRequest(String sessionId, String input) {}
}
