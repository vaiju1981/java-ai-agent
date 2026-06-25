package dev.vaijanath.aiagent.a2a;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.vaijanath.aiagent.agent.Agent;
import dev.vaijanath.aiagent.agent.AgentRequest;
import dev.vaijanath.aiagent.agent.AgentResponse;
import dev.vaijanath.aiagent.agent.RequestContext;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * The framework-agnostic A2A protocol: turns a JSON request body into one agent turn and back, and
 * renders the {@link AgentCard}. Mount it under any HTTP server — the bundled {@link A2aServer}, a
 * servlet, a Spring controller — since it is pure, synchronous, and holds no sockets.
 *
 * <p><b>Security:</b> the {@code principal}/{@code tenant} in a request are asserted by the caller, not
 * verified here. Run A2A on a trusted network or behind authentication, and hand this a
 * {@code Trust.govern(...)}-wrapped agent so guardrails and tool policy still apply to remote callers.
 */
public final class A2aHandler {

    private final Agent agent;
    private final AgentCard card;
    private final ObjectMapper json =
            new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public A2aHandler(Agent agent, String name, String description) {
        this.agent = Objects.requireNonNull(agent, "agent");
        this.card = new AgentCard(name, description);
    }

    /** The agent card for discovery. */
    public AgentCard card() {
        return card;
    }

    /** The agent card as a JSON document (the {@code GET} response). */
    public String cardJson() {
        return write(card);
    }

    /** Runs one turn from a JSON {@link A2aRequest} body and returns a JSON {@link A2aResponse}. */
    public String handle(String requestBody) {
        A2aRequest request;
        try {
            request = json.readValue(requestBody, A2aRequest.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("invalid A2A request JSON", e);
        }
        if (request == null || request.input() == null) {
            throw new IllegalArgumentException("A2A request requires 'input'");
        }
        AgentResponse response = agent.run(new AgentRequest(request.input(), context(request)));
        return write(A2aResponse.from(response));
    }

    private static RequestContext context(A2aRequest request) {
        String session = (request.sessionId() == null || request.sessionId().isBlank())
                ? UUID.randomUUID().toString()
                : request.sessionId();
        return new RequestContext(
                session, request.principal(), request.tenant(), request.traceId(), null, Map.of());
    }

    private String write(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("failed to serialize A2A payload", e);
        }
    }
}
