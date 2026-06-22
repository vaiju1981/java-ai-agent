package dev.vaijanath.aiagent.fincopilot;

import dev.vaijanath.aiagent.agent.Agent;
import dev.vaijanath.aiagent.agent.AgentRequest;
import dev.vaijanath.aiagent.agent.AgentResponse;
import dev.vaijanath.aiagent.agent.RequestContext;
import dev.vaijanath.aiagent.fincopilot.auth.SessionAuthenticationFilter;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * The chat surface. The agent, streaming-agent factory, and executor are provided by the
 * agent-spring-boot-starter; the turn plumbing (validation, status mapping, sync + SSE execution) lives
 * in {@link AgentTurns}. The principal is the authenticated user id, set on the request by the
 * {@link SessionAuthenticationFilter} (which guards {@code /api/chat/*}); the conversation is scoped to
 * the caller-supplied {@code sessionId}.
 */
@RestController
@RequestMapping("/api/chat")
class ChatController {

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
            @RequestAttribute(SessionAuthenticationFilter.PRINCIPAL_ATTRIBUTE) String principal) {
        return AgentTurns.run(agent, toRequest(body, principal));
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    SseEmitter stream(
            @RequestBody(required = false) TurnRequest body,
            @RequestAttribute(SessionAuthenticationFilter.PRINCIPAL_ATTRIBUTE) String principal) {
        return AgentTurns.stream(
                streamingAgentFactory,
                toRequest(body, principal),
                streamExecutor,
                properties.requestTimeout().toMillis() + 5_000L);
    }

    private AgentRequest toRequest(TurnRequest body, String principal) {
        String input = body == null ? null : body.input();
        String session = body == null ? null : body.sessionId();
        AgentTurns.requireIdentifier(principal, "principal");
        AgentTurns.requireIdentifier(session, "sessionId");
        AgentTurns.requireInput(input, MAX_INPUT_CHARS);
        RequestContext context = new RequestContext(
                session,
                principal,
                principal,
                UUID.randomUUID().toString(),
                Instant.now().plus(properties.requestTimeout()),
                Map.of());
        return new AgentRequest(input, context);
    }

    @ExceptionHandler(ConcurrentConversationException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    Map<String, String> conversationConflict() {
        return Map.of("error", "conversation changed concurrently; retry the complete turn");
    }

    record TurnRequest(String sessionId, String input) {}
}
