package dev.vaijanath.aiagent.a2a;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vaijanath.aiagent.agent.Agent;
import dev.vaijanath.aiagent.agent.AgentRequest;
import dev.vaijanath.aiagent.agent.AgentResponse;
import dev.vaijanath.aiagent.agent.RequestContext;
import dev.vaijanath.aiagent.graph.GraphAgent;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class A2aServerRoundTripTest {

    private static RemoteAgent remoteTo(A2aServer server) {
        return new RemoteAgent("http://localhost:" + server.port() + "/");
    }

    @Test
    void roundTripsACompletedTurn() throws Exception {
        try (A2aServer server =
                new A2aServer(req -> AgentResponse.completed("echo:" + req.input()), "bot", "d")) {

            AgentResponse r = remoteTo(server).run(new AgentRequest("ping"));

            assertTrue(r.isCompleted());
            assertEquals("echo:ping", r.output());
        }
    }

    @Test
    void propagatesContextOverTheWire() throws Exception {
        AtomicReference<RequestContext> seen = new AtomicReference<>();
        Agent capturing = req -> {
            seen.set(req.context());
            return AgentResponse.completed("ok");
        };
        try (A2aServer server = new A2aServer(capturing, "bot", "d")) {
            Instant deadline = Instant.now().plusSeconds(30);
            RequestContext ctx = new RequestContext("s1", "alice", "acme", "t1", deadline, Map.of());

            remoteTo(server).run(new AgentRequest("x", ctx));

            assertEquals("alice", seen.get().principal());
            assertEquals("acme", seen.get().tenant());
            assertEquals("t1", seen.get().traceId());
            assertTrue(seen.get().deadlineAt().isPresent(), "the deadline is propagated across the hop");
            assertEquals(deadline.toEpochMilli(), seen.get().deadlineAt().get().toEpochMilli());
        }
    }

    @Test
    void blockedTurnsRoundTrip() throws Exception {
        try (A2aServer server =
                new A2aServer(req -> AgentResponse.blocked("nope", "guardrail"), "bot", "d")) {

            AgentResponse r = remoteTo(server).run(new AgentRequest("x"));

            assertTrue(r.blocked());
            assertEquals("guardrail", r.stopReason());
        }
    }

    @Test
    void aRemoteAgentComposesAsAGraphNode() throws Exception {
        // The payoff: because RemoteAgent IS an Agent, a remote service is just another node.
        try (A2aServer server =
                new A2aServer(req -> AgentResponse.completed(req.input() + "+remote"), "spec", "d")) {
            Agent local = req -> AgentResponse.completed(req.input() + "+local");
            Agent graph = GraphAgent.builder()
                    .node("local", local)
                    .node("remote", remoteTo(server))
                    .start("local")
                    .edge("local", "remote")
                    .edge("remote", GraphAgent.END)
                    .build();

            AgentResponse r = graph.run(new AgentRequest("start"));

            assertEquals("start+local+remote", r.output());
        }
    }

    @Test
    void fetchesTheAgentCard() throws Exception {
        try (A2aServer server =
                new A2aServer(req -> AgentResponse.completed("x"), "billing", "handles invoices")) {

            AgentCard card = remoteTo(server).card();

            assertEquals("billing", card.name());
            assertEquals("handles invoices", card.description());
        }
    }

    @Test
    void remoteAgentSurfacesConnectionFailuresAsA2aException() {
        RemoteAgent remote = new RemoteAgent(URI.create("http://localhost:1/"), Duration.ofSeconds(2));

        assertThrows(A2aException.class, () -> remote.run(new AgentRequest("x")));
        assertThrows(A2aException.class, remote::card);
    }

    @Test
    void remoteAgentThrowsWhenTheServerErrors() throws Exception {
        Agent failing = req -> {
            throw new RuntimeException("boom");
        };
        try (A2aServer server = new A2aServer(failing, "bot", "d")) {
            RemoteAgent remote = remoteTo(server);
            assertThrows(A2aException.class, () -> remote.run(new AgentRequest("x"))); // HTTP 500
        }
    }

    @Test
    void serverRejectsBadRequestsAndUnsupportedMethods() throws Exception {
        try (A2aServer server = new A2aServer(req -> AgentResponse.completed("x"), "bot", "d")) {
            HttpClient http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
            URI uri = URI.create("http://localhost:" + server.port() + "/");

            HttpResponse<String> badBody = http.send(
                    HttpRequest.newBuilder(uri)
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString("not json"))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(400, badBody.statusCode());

            HttpResponse<String> wrongMethod = http.send(
                    HttpRequest.newBuilder(uri).PUT(HttpRequest.BodyPublishers.ofString("{}")).build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(405, wrongMethod.statusCode());
        }
    }

    @Test
    void serverClosesOnAnUnparseableRequest() throws Exception {
        try (A2aServer server = new A2aServer(req -> AgentResponse.completed("x"), "bot", "d");
                Socket raw = new Socket("localhost", server.port())) {
            raw.getOutputStream().write("garbage\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
            raw.getOutputStream().flush();

            assertEquals(-1, raw.getInputStream().read()); // closed without a response
        }
    }
}
