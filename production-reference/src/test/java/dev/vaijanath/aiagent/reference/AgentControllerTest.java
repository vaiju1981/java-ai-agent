package dev.vaijanath.aiagent.reference;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

import dev.vaijanath.aiagent.agent.Agent;
import dev.vaijanath.aiagent.agent.AgentResponse;
import dev.vaijanath.aiagent.model.ToolCall;
import dev.vaijanath.aiagent.observe.AgentObserver;
import dev.vaijanath.aiagent.tool.ToolResult;
import java.time.Duration;
import java.util.List;
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

    @BeforeEach
    void setUp() {
        Agent agent = request -> switch (request.input()) {
            case "deadline" -> AgentResponse.stopped("ran out of time", "deadline_exceeded");
            case "modelfail" -> AgentResponse.stopped("model down", "model_error");
            case "blockme" -> AgentResponse.blocked("safe replacement", "guardrail:test");
            default -> AgentResponse.completed("hello");
        };
        // The streaming agent emits tool lifecycle to the per-request observer, then completes.
        Function<AgentObserver, Agent> streamingFactory = observer -> request -> {
            observer.onToolCall(new ToolCall("call-1", "search", "{}"));
            observer.onToolResult("search", ToolResult.ok("raw results"));
            return AgentResponse.completed("streamed answer");
        };
        AgentProperties properties = new AgentProperties(
                null, null, 0, Duration.ofSeconds(90), null, null, null, "", List.of(), 0, 0);
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
}
