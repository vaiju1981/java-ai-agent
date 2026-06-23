package dev.vaijanath.aiagent.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vaijanath.aiagent.model.Message;
import dev.vaijanath.aiagent.model.ModelPort;
import dev.vaijanath.aiagent.model.ModelRequest;
import dev.vaijanath.aiagent.model.ModelResponse;
import dev.vaijanath.aiagent.model.Role;
import dev.vaijanath.aiagent.model.ToolCall;
import dev.vaijanath.aiagent.observe.AgentObserver;
import dev.vaijanath.aiagent.tool.StructuredTool;
import dev.vaijanath.aiagent.tool.StructuredToolResult;
import dev.vaijanath.aiagent.tool.Tool;
import dev.vaijanath.aiagent.tool.ToolEffect;
import dev.vaijanath.aiagent.tool.ToolInvocation;
import dev.vaijanath.aiagent.tool.ToolResult;
import dev.vaijanath.aiagent.tool.ToolSpec;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/** A {@link StructuredTool}'s JSON payload reaches observers/UI but never the model. */
class StructuredToolResultTest {

    private static final String DATA = "{\"category\":\"groceries\",\"spent\":210}";

    private static final class ReportTool implements StructuredTool {
        @Override
        public ToolSpec spec() {
            return new ToolSpec("report", "a report", "{\"type\":\"object\",\"properties\":{}}", ToolEffect.READ_ONLY);
        }

        @Override
        public StructuredToolResult invokeStructured(ToolInvocation invocation) {
            return StructuredToolResult.of("Spending: groceries 210", DATA);
        }
    }

    /** First reply calls the tool; the second answers using the last TOOL message the model can see. */
    private static final class ScriptedModel implements ModelPort {
        private int calls = 0;

        @Override
        public ModelResponse chat(ModelRequest request) {
            calls++;
            if (calls == 1) {
                return new ModelResponse("", List.of(new ToolCall("c1", "report", "{}")));
            }
            String lastTool = request.messages().stream()
                    .filter(m -> m.role() == Role.TOOL)
                    .reduce((a, b) -> b)
                    .map(Message::content)
                    .orElse("(none)");
            return ModelResponse.text("final based on: " + lastTool);
        }
    }

    @Test
    void structuredPayloadReachesObserversButNotTheModel() {
        AtomicReference<String> toolData = new AtomicReference<>();
        AgentObserver observer = new AgentObserver() {
            @Override
            public void onToolData(String toolName, String dataJson) {
                toolData.set(dataJson);
            }
        };

        AgentResponse r = DefaultAgent.builder()
                .model(new ScriptedModel())
                .tool(new ReportTool())
                .observer(observer)
                .build()
                .run(new AgentRequest("show my spending"));

        assertFalse(r.blocked());
        assertEquals("completed", r.stopReason());
        assertEquals(DATA, toolData.get(), "the structured JSON reaches the observer");
        assertTrue(r.output().contains("Spending: groceries 210"), "model used the text result: " + r.output());
        assertFalse(r.output().contains("\"spent\""), "structured JSON must not reach the model: " + r.output());
    }

    @Test
    void plainToolEmitsNoStructuredData() {
        AtomicReference<String> toolData = new AtomicReference<>();
        AgentObserver observer = new AgentObserver() {
            @Override
            public void onToolData(String toolName, String dataJson) {
                toolData.set(dataJson);
            }
        };
        Tool echo = new Tool() {
            @Override
            public ToolSpec spec() {
                return new ToolSpec("echo", "echo", "{\"type\":\"object\",\"properties\":{}}", ToolEffect.READ_ONLY);
            }

            @Override
            public ToolResult invoke(String argumentsJson) {
                return ToolResult.ok("echoed");
            }
        };
        ModelPort model = new ModelPort() {
            private int calls = 0;

            @Override
            public ModelResponse chat(ModelRequest request) {
                return ++calls == 1
                        ? new ModelResponse("", List.of(new ToolCall("c1", "echo", "{}")))
                        : ModelResponse.text("done");
            }
        };

        DefaultAgent.builder().model(model).tool(echo).observer(observer).build().run(new AgentRequest("go"));

        assertNull(toolData.get(), "a plain tool must not trigger onToolData");
    }
}
