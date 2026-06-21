package dev.vaijanath.aiagent.agent;

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
import org.junit.jupiter.api.Test;

/** Validates that the runtime registers and dispatches correctly across many (40) tools. */
class ManyToolsDispatchTest {

    private static Tool numberedTool(int n) {
        return new Tool() {
            @Override
            public ToolSpec spec() {
                return new ToolSpec("tool_" + n, "operation " + n, "{\"type\":\"object\"}");
            }

            @Override
            public ToolResult invoke(String argumentsJson) {
                return ToolResult.ok("result_" + n);
            }
        };
    }

    @Test
    void routesToTheCorrectToolAmong40() {
        ModelPort scripted = new ModelPort() {
            private int calls = 0;

            @Override
            public ModelResponse chat(ModelRequest request) {
                calls++;
                if (calls == 1) {
                    return new ModelResponse("", List.of(new ToolCall("c1", "tool_37", "{}")));
                }
                String tool = request.messages().stream()
                        .filter(m -> m.role() == Role.TOOL)
                        .reduce((a, b) -> b)
                        .map(Message::content)
                        .orElse("");
                return ModelResponse.text("observed: " + tool);
            }
        };

        DefaultAgent.Builder builder = DefaultAgent.builder().model(scripted).maxSteps(4);
        for (int i = 0; i < 40; i++) {
            builder.tool(numberedTool(i));
        }

        AgentResponse r = builder.build().run(new AgentRequest("use tool 37"));

        assertTrue(r.output().contains("result_37"), "got: " + r.output());
    }
}
