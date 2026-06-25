package dev.vaijanath.aiagent.a2a;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.vaijanath.aiagent.agent.Agent;
import dev.vaijanath.aiagent.agent.AgentRequest;
import dev.vaijanath.aiagent.agent.AgentResponse;
import dev.vaijanath.aiagent.agent.RequestContext;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * A local handle to a remote A2A agent. It <b>implements {@link Agent}</b>, so a remote agent drops into
 * any orchestrator — {@code SupervisorAgent}, {@code GraphAgent}, {@code HandoffAgent},
 * {@code Agents.asTool} — exactly like a local one; distributed multi-agent systems compose the same way
 * in-process ones do. Each {@link #run} POSTs the turn to the remote endpoint and maps the response back,
 * carrying the turn's identity/session/trace so the remote side keeps tenant isolation and tracing.
 *
 * <pre>{@code
 * Agent remote = new RemoteAgent("http://billing.internal:8080/");
 * AgentResponse r = remote.run(new AgentRequest("refund my last order"));
 * }</pre>
 */
public final class RemoteAgent implements Agent {

    private final URI endpoint;
    private final Duration timeout;
    private final HttpClient http;
    private final ObjectMapper json =
            new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public RemoteAgent(String endpoint) {
        this(URI.create(endpoint), Duration.ofSeconds(60));
    }

    public RemoteAgent(URI endpoint, Duration timeout) {
        this.endpoint = Objects.requireNonNull(endpoint, "endpoint");
        this.timeout = Objects.requireNonNull(timeout, "timeout");
        this.http = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(timeout)
                .build();
    }

    /** Fetches the remote agent's {@link AgentCard} ({@code GET}), for discovery. */
    public AgentCard card() {
        try {
            HttpResponse<String> response = http.send(
                    HttpRequest.newBuilder(endpoint).timeout(timeout).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            requireOk(response.statusCode());
            return json.readValue(response.body(), AgentCard.class);
        } catch (A2aException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new A2aException("interrupted fetching agent card from " + endpoint, e);
        } catch (Exception e) {
            throw new A2aException("failed to fetch agent card from " + endpoint, e);
        }
    }

    @Override
    public AgentResponse run(AgentRequest request) {
        RequestContext ctx = request.context();
        Long deadline = ctx.deadlineAt().map(Instant::toEpochMilli).orElse(null);
        A2aRequest body = new A2aRequest(
                request.input(), ctx.sessionId(), ctx.principal(), ctx.tenant(), ctx.traceId(), deadline);
        try {
            HttpResponse<String> response = http.send(
                    HttpRequest.newBuilder(endpoint)
                            .timeout(timeout)
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            requireOk(response.statusCode());
            return json.readValue(response.body(), A2aResponse.class).toAgentResponse();
        } catch (A2aException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new A2aException("interrupted calling A2A agent at " + endpoint, e);
        } catch (Exception e) {
            throw new A2aException("A2A call to " + endpoint + " failed", e);
        }
    }

    private void requireOk(int statusCode) {
        if (statusCode != 200) {
            throw new A2aException("remote agent at " + endpoint + " returned HTTP " + statusCode);
        }
    }
}
