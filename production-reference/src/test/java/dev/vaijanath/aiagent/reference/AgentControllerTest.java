package dev.vaijanath.aiagent.reference;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

import dev.vaijanath.aiagent.agent.Agent;
import dev.vaijanath.aiagent.agent.AgentResponse;
import dev.vaijanath.aiagent.agent.IdempotentAgent;
import dev.vaijanath.aiagent.agent.InMemoryIdempotencyStore;
import dev.vaijanath.aiagent.model.ToolCall;
import dev.vaijanath.aiagent.observe.AgentObserver;
import dev.vaijanath.aiagent.tool.ToolResult;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Verifies the HTTP contract: outcome -> status mapping, validation, and the SSE stream shape. */
class AgentControllerTest {

    private MockMvc mvc;
    private Function<AgentObserver, Agent> streamingFactory;
    private AgentProperties properties;

    @BeforeEach
    void setUp() {
        Agent agent = request -> switch (request.input()) {
            case "deadline" -> AgentResponse.stopped("ran out of time", "deadline_exceeded");
            case "modelfail" -> AgentResponse.stopped("model down", "model_error");
            case "blockme" -> AgentResponse.blocked("safe replacement", "guardrail:test");
            default -> AgentResponse.completed("hello");
        };
        // The streaming agent emits tool lifecycle to the per-request observer, then completes.
        streamingFactory = observer -> request -> {
            observer.onToolCall(new ToolCall("call-1", "search", "{}"));
            observer.onToolResult("search", ToolResult.ok("raw results"));
            return AgentResponse.completed("streamed answer");
        };
        properties = new AgentProperties(
                null, null, 0, Duration.ofSeconds(90), null, null, null, "", Map.of(), 0, 0, false, "");
        // Run the streaming turn synchronously so the SSE assertions are deterministic.
        mvc = standaloneSetup(new AgentController(agent, streamingFactory, Runnable::run, properties)).build();
    }

    private static MockHttpServletRequestBuilder turn(String input) {
        return post("/api/agent/turn")
                .header("X-Tenant-Id", "acme")
                .header("X-Principal-Id", "user-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sessionId\":\"s1\",\"input\":\"" + input + "\"}");
    }

    private static MockHttpServletRequestBuilder streamTurn(String input) {
        return post("/api/agent/turn/stream")
                .header("X-Tenant-Id", "acme")
                .header("X-Principal-Id", "user-1")
                .accept(MediaType.TEXT_EVENT_STREAM)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sessionId\":\"s1\",\"input\":\"" + input + "\"}");
    }

    @Test
    void completionReturns200() throws Exception {
        mvc.perform(turn("hi"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.output").value("hello"));
    }

    @Test
    void deadlineMapsToGatewayTimeout() throws Exception {
        mvc.perform(turn("deadline")).andExpect(status().isGatewayTimeout());
    }

    @Test
    void modelErrorMapsToServiceUnavailable() throws Exception {
        mvc.perform(turn("modelfail")).andExpect(status().isServiceUnavailable());
    }

    @Test
    void guardrailBlockReturns200WithBlockedBody() throws Exception {
        mvc.perform(turn("blockme"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.blocked").value(true));
    }

    @Test
    void missingTenantHeaderIsBadRequest() throws Exception {
        mvc.perform(post("/api/agent/turn")
                        .header("X-Principal-Id", "user-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sessionId\":\"s1\",\"input\":\"hi\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void blankInputIsBadRequest() throws Exception {
        mvc.perform(turn(" ")).andExpect(status().isBadRequest());
    }

    @Test
    void streamEmitsToolEventsThenGuardedFinal() throws Exception {
        MvcResult started = mvc.perform(streamTurn("hi"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mvc.perform(asyncDispatch(started))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
                .andExpect(content().string(allOf(
                        containsString("event:tool"),
                        containsString("search"),
                        containsString("event:final"),
                        containsString("streamed answer"))));
    }

    @Test
    void streamWithBlankInputIsBadRequestBeforeStreaming() throws Exception {
        mvc.perform(streamTurn(" ")).andExpect(status().isBadRequest());
    }

    @Test
    void sameIdempotencyKeyIsEnforcedEndToEnd() throws Exception {
        // The wiring under test: the controller passes Idempotency-Key into the RequestContext, and the
        // unary agent is wrapped in IdempotentAgent — so a retry with the same key replays, not re-runs.
        AtomicInteger runs = new AtomicInteger();
        Agent counting = request -> AgentResponse.completed("answer " + runs.incrementAndGet());
        MockMvc idem = standaloneSetup(new AgentController(
                        new IdempotentAgent(counting, new InMemoryIdempotencyStore()),
                        streamingFactory,
                        Runnable::run,
                        properties))
                .build();

        idem.perform(turn("hi").header("Idempotency-Key", "k1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.output").value("answer 1"));
        // Same key, same tenant/principal/session: the stored result is replayed.
        idem.perform(turn("hi").header("Idempotency-Key", "k1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.output").value("answer 1"));
        // A different key runs the turn again.
        idem.perform(turn("hi").header("Idempotency-Key", "k2"))
                .andExpect(jsonPath("$.output").value("answer 2"));

        assertEquals(2, runs.get(), "a repeated idempotency key must not re-invoke the agent");
    }
}
