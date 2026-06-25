package dev.vaijanath.aiagent.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vaijanath.aiagent.model.ModelPort;
import dev.vaijanath.aiagent.model.ModelRequest;
import dev.vaijanath.aiagent.model.ModelResponse;
import dev.vaijanath.aiagent.model.ToolCall;
import dev.vaijanath.aiagent.observe.AgentObserver;
import dev.vaijanath.aiagent.tool.Tool;
import dev.vaijanath.aiagent.tool.ToolEffect;
import dev.vaijanath.aiagent.tool.ToolResult;
import dev.vaijanath.aiagent.tool.ToolSpec;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ToolCallCeilingTest {

    /** Emits three tool calls in one step, then a final answer. */
    private static final class ThreeCalls implements ModelPort {
        private int calls = 0;

        @Override
        public ModelResponse chat(ModelRequest request) {
            if (++calls == 1) {
                return new ModelResponse(
                        "",
                        List.of(
                                new ToolCall("c1", "work", "{}"),
                                new ToolCall("c2", "work", "{}"),
                                new ToolCall("c3", "work", "{}")));
            }
            return ModelResponse.text("done");
        }
    }

    private static Tool countingWork(AtomicInteger runs) {
        return new Tool() {
            @Override
            public ToolSpec spec() {
                return new ToolSpec(
                        "work", "does work", "{\"type\":\"object\",\"properties\":{}}", ToolEffect.READ_ONLY);
            }

            @Override
            public ToolResult invoke(String argumentsJson) {
                runs.incrementAndGet();
                return ToolResult.ok("ran");
            }
        };
    }

    @Test
    void capsToolCallsPerStepAndReturnsTheRestAsErrors() {
        AtomicInteger runs = new AtomicInteger();
        List<String> results = new CopyOnWriteArrayList<>();
        AgentObserver capture = new AgentObserver() {
            @Override
            public void onToolResult(String toolName, ToolResult result) {
                results.add(result.content());
            }
        };

        DefaultAgent.builder()
                .model(new ThreeCalls())
                .tool(countingWork(runs))
                .maxToolCallsPerStep(2)
                .observer(capture)
                .build()
                .run(new AgentRequest("go"));

        assertEquals(2, runs.get(), "only the first 2 of 3 tool calls execute");
        assertEquals(3, results.size(), "every call still gets a result so the transcript stays valid");
        assertTrue(results.get(2).contains("skipped"), "the 3rd call is reported skipped: " + results.get(2));
    }

    @Test
    void noCeilingByDefault() {
        AtomicInteger runs = new AtomicInteger();

        DefaultAgent.builder()
                .model(new ThreeCalls())
                .tool(countingWork(runs))
                .build()
                .run(new AgentRequest("go"));

        assertEquals(3, runs.get(), "all tool calls run when no ceiling is set");
    }
}
