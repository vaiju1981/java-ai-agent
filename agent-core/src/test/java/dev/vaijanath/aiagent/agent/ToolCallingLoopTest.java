package dev.vaijanath.aiagent.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vaijanath.aiagent.model.Message;
import dev.vaijanath.aiagent.model.ModelPort;
import dev.vaijanath.aiagent.model.ModelRequest;
import dev.vaijanath.aiagent.model.ModelResponse;
import dev.vaijanath.aiagent.model.Role;
import dev.vaijanath.aiagent.model.ToolCall;
import dev.vaijanath.aiagent.tool.Tool;
import dev.vaijanath.aiagent.tool.ToolResult;
import dev.vaijanath.aiagent.tool.ToolSpec;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

/** Deterministically verifies the model -> tool -> model loop without a live LLM. */
class ToolCallingLoopTest {

    /** First reply asks for a tool; the second reply incorporates the tool's result. */
    private static final class ScriptedModel implements ModelPort {
        private int calls = 0;

        @Override
        public ModelResponse chat(ModelRequest request) {
            calls++;
            if (calls == 1) {
                return new ModelResponse(
                        "", List.of(new ToolCall("call-1", "echo", "{\"text\":\"hi\"}")));
            }
            String lastTool = request.messages().stream()
                    .filter(m -> m.role() == Role.TOOL)
                    .reduce((a, b) -> b)
                    .map(Message::content)
                    .orElse("(none)");
            return ModelResponse.text("done: " + lastTool);
        }
    }

    private static final class EchoTool implements Tool {
        @Override
        public ToolSpec spec() {
            return new ToolSpec(
                    "echo",
                    "Echoes its text argument.",
                    "{\"type\":\"object\",\"properties\":{\"text\":{\"type\":\"string\"}}}");
        }

        @Override
        public ToolResult invoke(String argumentsJson) {
            return ToolResult.ok("echoed:" + argumentsJson);
        }
    }

    @Test
    void executesToolThenAnswers() {
        Agent agent = DefaultAgent.builder()
                .model(new ScriptedModel())
                .tool(new EchoTool())
                .build();

        AgentResponse r = agent.run(new AgentRequest("please echo hi"));

        assertFalse(r.blocked());
        assertEquals("completed", r.stopReason());
        assertTrue(r.output().startsWith("done: echoed:"), "got: " + r.output());
    }

    @Test
    void unknownToolIsReportedNotThrown() {
        ModelPort callsMissingTool = new ModelPort() {
            private int calls = 0;

            @Override
            public ModelResponse chat(ModelRequest request) {
                calls++;
                if (calls == 1) {
                    return new ModelResponse("", List.of(new ToolCall("c1", "does_not_exist", "{}")));
                }
                String lastTool = request.messages().stream()
                        .filter(m -> m.role() == Role.TOOL)
                        .reduce((a, b) -> b)
                        .map(Message::content)
                        .orElse("(none)");
                return ModelResponse.text(lastTool);
            }
        };

        AgentResponse r = DefaultAgent.builder().model(callsMissingTool).build()
                .run(new AgentRequest("use a tool"));

        assertFalse(r.blocked());
        assertTrue(r.output().contains("is not available"), "got: " + r.output());
    }

    /** A tool the selector did not present this turn must not execute, even if the model names it. */
    @Test
    void selectorExcludedToolIsNotInvoked() {
        AtomicBoolean invoked = new AtomicBoolean(false);
        Tool secret = new Tool() {
            @Override
            public ToolSpec spec() {
                return new ToolSpec("secret", "do not call", "{\"type\":\"object\",\"properties\":{}}");
            }

            @Override
            public ToolResult invoke(String argumentsJson) {
                invoked.set(true);
                return ToolResult.ok("ran");
            }
        };
        ModelPort callsSecret = new ModelPort() {
            private int calls = 0;

            @Override
            public ModelResponse chat(ModelRequest request) {
                calls++;
                if (calls == 1) {
                    return new ModelResponse("", List.of(new ToolCall("c1", "secret", "{}")));
                }
                return ModelResponse.text("ok");
            }
        };

        AgentResponse r = DefaultAgent.builder()
                .model(callsSecret)
                .tool(secret)
                .toolSelector((task, tools) -> List.of()) // present nothing this turn
                .build()
                .run(new AgentRequest("go"));

        assertFalse(invoked.get(), "a tool excluded by the selector must never execute");
        assertEquals("completed", r.stopReason());
    }
}
