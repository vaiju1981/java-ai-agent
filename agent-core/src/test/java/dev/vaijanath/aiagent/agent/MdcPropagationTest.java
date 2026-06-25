package dev.vaijanath.aiagent.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vaijanath.aiagent.model.ModelPort;
import dev.vaijanath.aiagent.model.ModelRequest;
import dev.vaijanath.aiagent.model.ModelResponse;
import dev.vaijanath.aiagent.model.ToolCall;
import dev.vaijanath.aiagent.tool.Tool;
import dev.vaijanath.aiagent.tool.ToolEffect;
import dev.vaijanath.aiagent.tool.ToolResult;
import dev.vaijanath.aiagent.tool.ToolSpec;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

class MdcPropagationTest {

    @AfterEach
    void clear() {
        MDC.clear();
    }

    /** Two tool calls run on parallel virtual threads; without propagation their MDC would be empty. */
    @Test
    void mdcPropagatesToParallelToolThreads() {
        MDC.put("traceId", "T1");
        List<String> seen = new CopyOnWriteArrayList<>();
        Tool capturing = new Tool() {
            @Override
            public ToolSpec spec() {
                return new ToolSpec(
                        "work", "does work", "{\"type\":\"object\",\"properties\":{}}", ToolEffect.READ_ONLY);
            }

            @Override
            public ToolResult invoke(String argumentsJson) {
                seen.add(String.valueOf(MDC.get("traceId")));
                return ToolResult.ok("ok");
            }
        };
        ModelPort twoCalls = new ModelPort() {
            private int n = 0;

            @Override
            public ModelResponse chat(ModelRequest request) {
                return ++n == 1
                        ? new ModelResponse(
                                "", List.of(new ToolCall("c1", "work", "{}"), new ToolCall("c2", "work", "{}")))
                        : ModelResponse.text("done");
            }
        };

        DefaultAgent.builder().model(twoCalls).tool(capturing).build().run(new AgentRequest("go"));

        assertEquals(2, seen.size());
        assertTrue(seen.stream().allMatch("T1"::equals), "traceId propagated to tool threads: " + seen);
    }
}
