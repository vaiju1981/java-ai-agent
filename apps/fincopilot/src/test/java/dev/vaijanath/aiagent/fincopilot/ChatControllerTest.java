package dev.vaijanath.aiagent.fincopilot;

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
import dev.vaijanath.aiagent.fincopilot.auth.SessionAuthenticationFilter;
import dev.vaijanath.aiagent.model.ToolCall;
import dev.vaijanath.aiagent.observe.AgentObserver;
import dev.vaijanath.aiagent.tool.ToolResult;
import java.time.Duration;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Verifies the chat HTTP contract against a stubbed agent (no model, no database). */
class ChatControllerTest {

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        Agent agent = request -> AgentResponse.completed("Hello from FinCopilot");
        Function<AgentObserver, Agent> streamingFactory = observer -> request -> {
            observer.onToolCall(new ToolCall("call-1", "lookup", "{}"));
            observer.onToolResult("lookup", ToolResult.ok("raw results"));
            return AgentResponse.completed("streamed answer");
        };
        FinCopilotProperties properties =
                new FinCopilotProperties(null, null, 0, Duration.ofSeconds(90), null, 0, null);
        // Run the streaming turn synchronously so the SSE assertions are deterministic.
        mvc = standaloneSetup(new ChatController(agent, streamingFactory, Runnable::run, properties)).build();
    }

    private static MockHttpServletRequestBuilder turn(String input) {
        return post("/api/chat/turn")
                .requestAttr(SessionAuthenticationFilter.PRINCIPAL_ATTRIBUTE, "user-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sessionId\":\"s1\",\"input\":\"" + input + "\"}");
    }

    @Test
    void completionReturnsTheAnswer() throws Exception {
        mvc.perform(turn("hello"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.output").value("Hello from FinCopilot"));
    }

    @Test
    void blankInputIsBadRequest() throws Exception {
        mvc.perform(turn("   ")).andExpect(status().isBadRequest());
    }

    @Test
    void streamEmitsToolEventsThenFinal() throws Exception {
        MvcResult started = mvc.perform(post("/api/chat/stream")
                        .requestAttr(SessionAuthenticationFilter.PRINCIPAL_ATTRIBUTE, "user-1")
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sessionId\":\"s1\",\"input\":\"hello\"}"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mvc.perform(asyncDispatch(started))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
                .andExpect(content().string(allOf(
                        containsString("event:tool"),
                        containsString("lookup"),
                        containsString("event:final"),
                        containsString("streamed answer"))));
    }
}
