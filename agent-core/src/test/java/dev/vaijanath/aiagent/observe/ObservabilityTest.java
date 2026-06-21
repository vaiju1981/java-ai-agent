package dev.vaijanath.aiagent.observe;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.vaijanath.aiagent.agent.Agent;
import dev.vaijanath.aiagent.agent.AgentRequest;
import dev.vaijanath.aiagent.agent.AgentResponse;
import dev.vaijanath.aiagent.agent.DefaultAgent;
import dev.vaijanath.aiagent.model.Message;
import dev.vaijanath.aiagent.model.ModelPort;
import dev.vaijanath.aiagent.model.ModelRequest;
import dev.vaijanath.aiagent.model.ModelResponse;
import dev.vaijanath.aiagent.model.ReplayModelPort;
import dev.vaijanath.aiagent.model.Role;
import dev.vaijanath.aiagent.model.ToolCall;
import dev.vaijanath.aiagent.model.Usage;
import dev.vaijanath.aiagent.tool.ReplayToolExecutor;
import dev.vaijanath.aiagent.tool.Tool;
import dev.vaijanath.aiagent.tool.ToolEffect;
import dev.vaijanath.aiagent.tool.ToolResult;
import dev.vaijanath.aiagent.tool.ToolSpec;
import java.util.List;
import org.junit.jupiter.api.Test;

class ObservabilityTest {

    @Test
    void tokenAccountingAccumulatesUsage() {
        ModelPort model = request -> ModelResponse.text("hi", new Usage(10, 5));
        TokenAccountingObserver accounting = new TokenAccountingObserver();

        Agent agent = DefaultAgent.builder().model(model).observer(accounting).build();
        agent.run(new AgentRequest("hello"));

        assertEquals(1, accounting.modelCalls());
        assertEquals(10, accounting.inputTokens());
        assertEquals(5, accounting.outputTokens());
        assertEquals(15, accounting.totalTokens());
    }

    @Test
    void observerExceptionDoesNotBreakTheRun() {
        ModelPort model = request -> ModelResponse.text("ok");
        AgentObserver throwing = new AgentObserver() {
            @Override
            public void onTurnStart(String input) {
                throw new RuntimeException("boom");
            }
        };

        AgentResponse r = DefaultAgent.builder().model(model).observer(throwing).build()
                .run(new AgentRequest("x"));

        assertEquals("ok", r.output());
    }

    @Test
    void recordThenReplayReproducesOutput() {
        // A scripted model: first asks for a tool, then answers using the tool result.
        ModelPort scripted = new ModelPort() {
            private int calls = 0;

            @Override
            public ModelResponse chat(ModelRequest request) {
                calls++;
                if (calls == 1) {
                    return new ModelResponse("", List.of(new ToolCall("c1", "echo", "{\"t\":\"hi\"}")));
                }
                String tool = request.messages().stream()
                        .filter(m -> m.role() == Role.TOOL)
                        .reduce((a, b) -> b)
                        .map(Message::content)
                        .orElse("?");
                return ModelResponse.text("answer: " + tool);
            }
        };
        Tool echo = new Tool() {
            @Override
            public ToolSpec spec() {
                return new ToolSpec("echo", "echo", "{\"type\":\"object\",\"properties\":{}}",
                        ToolEffect.READ_ONLY);
            }

            @Override
            public ToolResult invoke(String argumentsJson) {
                return ToolResult.ok("echoed");
            }
        };

        RecordingObserver recorder = new RecordingObserver();
        AgentResponse original = DefaultAgent.builder()
                .model(scripted).tool(echo).observer(recorder).build()
                .run(new AgentRequest("please echo"));

        AgentResponse replayed = DefaultAgent.builder()
                .model(new ReplayModelPort(recorder.modelResponses())).tool(echo)
                .toolExecutor(new ReplayToolExecutor(recorder.toolResults()))
                .build()
                .run(new AgentRequest("please echo"));

        assertEquals(original.output(), replayed.output());
        assertEquals("answer: echoed", replayed.output());
    }
}
