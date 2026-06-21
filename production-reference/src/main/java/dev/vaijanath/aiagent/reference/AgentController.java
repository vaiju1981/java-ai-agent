package dev.vaijanath.aiagent.reference;

import dev.vaijanath.aiagent.agent.Agent;
import dev.vaijanath.aiagent.agent.AgentRequest;
import dev.vaijanath.aiagent.agent.AgentResponse;
import dev.vaijanath.aiagent.agent.RequestContext;
import dev.vaijanath.aiagent.store.jdbc.ConcurrentConversationException;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/agent")
class AgentController {

    private static final int MAX_INPUT_CHARS = 32_000;

    private final Agent agent;
    private final AgentProperties properties;

    AgentController(Agent agent, AgentProperties properties) {
        this.agent = agent;
        this.properties = properties;
    }

    @PostMapping("/turn")
    AgentResponse turn(
            @RequestBody TurnRequest body,
            @RequestHeader("X-Tenant-Id") String tenant,
            @RequestHeader("X-Principal-Id") String principal,
            @RequestHeader(value = "X-Trace-Id", required = false) String traceId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
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
            throw new BadRequest("input must contain 1-" + MAX_INPUT_CHARS + " characters");
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
        return agent.run(new AgentRequest(input, context));
    }

    private static void requireIdentifier(String value, String name) {
        if (value == null || !value.matches("[A-Za-z0-9._:-]{1,128}")) {
            throw new BadRequest(name + " must match [A-Za-z0-9._:-]{1,128}");
        }
    }

    @ExceptionHandler(BadRequest.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    Map<String, String> badRequest(BadRequest error) {
        return Map.of("error", error.getMessage());
    }

    @ExceptionHandler(ConcurrentConversationException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    Map<String, String> conversationConflict() {
        return Map.of("error", "conversation changed concurrently; retry the complete turn");
    }

    record TurnRequest(String sessionId, String input) {}

    private static final class BadRequest extends RuntimeException {
        BadRequest(String message) {
            super(message);
        }
    }
}
