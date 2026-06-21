package dev.vaijanath.aiagent.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.vaijanath.aiagent.model.Message;
import dev.vaijanath.aiagent.model.ModelPort;
import dev.vaijanath.aiagent.model.ModelRequest;
import dev.vaijanath.aiagent.model.ModelResponse;
import dev.vaijanath.aiagent.model.ReplayModelPort;
import dev.vaijanath.aiagent.model.Role;
import dev.vaijanath.aiagent.model.ToolCall;
import dev.vaijanath.aiagent.observe.RecordingObserver;
import dev.vaijanath.aiagent.tool.ReplayToolExecutor;
import dev.vaijanath.aiagent.tool.Tool;
import dev.vaijanath.aiagent.tool.ToolEffect;
import dev.vaijanath.aiagent.tool.ToolResult;
import dev.vaijanath.aiagent.tool.ToolSpec;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ReplaySafetyTest {

    /** Calls the {@code act} tool once, then echoes the tool's result. */
    private static ModelPort callsActThenEchoes() {
        return new ModelPort() {
            private int calls = 0;

            @Override
            public ModelResponse chat(ModelRequest request) {
                if (++calls == 1) {
                    return new ModelResponse("", List.of(new ToolCall("c1", "act", "{}")));
                }
                String tool = request.messages().stream()
                        .filter(m -> m.role() == Role.TOOL)
                        .reduce((a, b) -> b)
                        .map(Message::content)
                        .orElse("(none)");
                return ModelResponse.text("answer:" + tool);
            }
        };
    }

    @Test
    void replayReproducesOutputWithoutReExecutingTools() {
        AtomicInteger sideEffects = new AtomicInteger();
        Tool effectful = new Tool() {
            @Override
            public ToolSpec spec() {
                return new ToolSpec("act", "do a thing",
                        "{\"type\":\"object\",\"properties\":{}}", ToolEffect.EFFECTFUL);
            }

            @Override
            public ToolResult invoke(String argumentsJson) {
                return ToolResult.ok("done#" + sideEffects.incrementAndGet());
            }
        };

        RecordingObserver recording = new RecordingObserver();
        AgentResponse first = DefaultAgent.builder()
                .model(callsActThenEchoes())
                .tool(effectful)
                .observer(recording)
                .build()
                .run(new AgentRequest("go"));
        assertEquals(1, sideEffects.get(), "the tool ran once during the recorded run");

        AgentResponse replayed = DefaultAgent.builder()
                .model(new ReplayModelPort(recording.modelResponses()))
                .tool(effectful)
                .toolExecutor(new ReplayToolExecutor(recording.toolResults()))
                .build()
                .run(new AgentRequest("go"));

        assertEquals(1, sideEffects.get(), "replay must NOT re-execute the tool");
        assertEquals(first.output(), replayed.output(), "replay reproduces the original output");
    }
}
