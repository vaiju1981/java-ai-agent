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

class ParallelToolCallsTest {

    /** Emits three tool calls in one step (slowest first), then a final answer. */
    private static final class ThreeCalls implements ModelPort {
        private int calls = 0;

        @Override
        public ModelResponse chat(ModelRequest request) {
            if (++calls == 1) {
                return new ModelResponse("", List.of(
                        new ToolCall("c1", "work", "{\"ms\":\"100\"}"),
                        new ToolCall("c2", "work", "{\"ms\":\"50\"}"),
                        new ToolCall("c3", "work", "{\"ms\":\"10\"}")));
            }
            return ModelResponse.text("done");
        }
    }

    /** Sleeps for its {@code ms} argument; tracks peak concurrency; echoes the ms it slept. */
    private static final class SlowTool implements Tool {
        final AtomicInteger active = new AtomicInteger();
        final AtomicInteger peak = new AtomicInteger();

        @Override
        public ToolSpec spec() {
            return new ToolSpec(
                    "work",
                    "sleeps for the given ms",
                    "{\"type\":\"object\",\"properties\":{\"ms\":{\"type\":\"string\"}}}",
                    ToolEffect.READ_ONLY);
        }

        @Override
        public ToolResult invoke(String argumentsJson) {
            String ms = argumentsJson.replaceAll("\\D+", "");
            peak.accumulateAndGet(active.incrementAndGet(), Math::max);
            try {
                Thread.sleep(Long.parseLong(ms));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                active.decrementAndGet();
            }
            return ToolResult.ok("slept:" + ms);
        }
    }

    @Test
    void runsIndependentToolCallsConcurrently() {
        SlowTool tool = new SlowTool();

        DefaultAgent.builder().model(new ThreeCalls()).tool(tool).build().run(new AgentRequest("go"));

        assertTrue(tool.peak.get() >= 2, "expected concurrent tool calls, peak=" + tool.peak.get());
    }

    @Test
    void runsSequentiallyWhenParallelDisabled() {
        SlowTool tool = new SlowTool();

        DefaultAgent.builder().model(new ThreeCalls()).tool(tool).parallelToolCalls(false).build()
                .run(new AgentRequest("go"));

        assertEquals(1, tool.peak.get(), "with parallel off, only one tool runs at a time");
    }

    @Test
    void recordsResultsInCallOrderNotFinishOrder() {
        SlowTool tool = new SlowTool();
        List<String> resultOrder = new CopyOnWriteArrayList<>();
        AgentObserver capture = new AgentObserver() {
            @Override
            public void onToolResult(String toolName, ToolResult result) {
                resultOrder.add(result.content());
            }
        };

        DefaultAgent.builder().model(new ThreeCalls()).tool(tool).observer(capture).build()
                .run(new AgentRequest("go"));

        // c1 (100ms) finishes last but must be recorded first — results follow call order, not finish order.
        assertEquals(List.of("slept:100", "slept:50", "slept:10"), resultOrder);
    }
}
