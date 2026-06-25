package dev.vaijanath.aiagent.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

class ParallelToolCallsTest {

    /** Emits three tool calls in one step (ids 100/50/10), then a final answer. */
    private static final class ThreeCalls implements ModelPort {
        private int calls = 0;

        @Override
        public ModelResponse chat(ModelRequest request) {
            if (++calls == 1) {
                return new ModelResponse("", List.of(
                        new ToolCall("c1", "work", "{\"id\":\"100\"}"),
                        new ToolCall("c2", "work", "{\"id\":\"50\"}"),
                        new ToolCall("c3", "work", "{\"id\":\"10\"}")));
            }
            return ModelResponse.text("done");
        }
    }

    private static Tool work(Function<String, ToolResult> body) {
        return new Tool() {
            @Override
            public ToolSpec spec() {
                return new ToolSpec(
                        "work",
                        "does work",
                        "{\"type\":\"object\",\"properties\":{\"id\":{\"type\":\"string\"}}}",
                        ToolEffect.READ_ONLY);
            }

            @Override
            public ToolResult invoke(String argumentsJson) {
                return body.apply(argumentsJson);
            }
        };
    }

    private static String id(String argumentsJson) {
        return argumentsJson.replaceAll("\\D+", "");
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Test
    void runsIndependentToolCallsConcurrently() {
        CountDownLatch arrived = new CountDownLatch(3);
        AtomicInteger active = new AtomicInteger();
        AtomicInteger peak = new AtomicInteger();
        Tool work = work(args -> {
            peak.accumulateAndGet(active.incrementAndGet(), Math::max);
            arrived.countDown();
            awaitQuietly(arrived); // returns once all three are concurrently inside invoke
            active.decrementAndGet();
            return ToolResult.ok("ran:" + id(args));
        });

        DefaultAgent.builder().model(new ThreeCalls()).tool(work).build().run(new AgentRequest("go"));

        assertTrue(peak.get() >= 2, "expected concurrent tool calls, peak=" + peak.get());
    }

    @Test
    void runsSequentiallyWhenParallelDisabled() {
        AtomicInteger active = new AtomicInteger();
        AtomicInteger peak = new AtomicInteger();
        Tool work = work(args -> {
            peak.accumulateAndGet(active.incrementAndGet(), Math::max);
            active.decrementAndGet();
            return ToolResult.ok("ran:" + id(args));
        });

        DefaultAgent.builder().model(new ThreeCalls()).tool(work).parallelToolCalls(false).build()
                .run(new AgentRequest("go"));

        assertEquals(1, peak.get(), "with parallel off, only one tool runs at a time");
    }

    @Test
    void recordsResultsInCallOrderNotFinishOrder() {
        // The first call (100) cannot finish until the last call (10) has run, so finish order differs
        // from call order — yet results must still be recorded in call order.
        CountDownLatch firstMayFinish = new CountDownLatch(1);
        List<String> finishOrder = new CopyOnWriteArrayList<>();
        Tool work = work(args -> {
            String id = id(args);
            if (id.equals("100")) {
                awaitQuietly(firstMayFinish);
            }
            if (id.equals("10")) {
                firstMayFinish.countDown();
            }
            finishOrder.add(id);
            return ToolResult.ok("ran:" + id);
        });
        List<String> resultOrder = new CopyOnWriteArrayList<>();
        AgentObserver capture = new AgentObserver() {
            @Override
            public void onToolResult(String toolName, ToolResult result) {
                resultOrder.add(result.content());
            }
        };

        DefaultAgent.builder().model(new ThreeCalls()).tool(work).observer(capture).build()
                .run(new AgentRequest("go"));

        assertEquals(List.of("ran:100", "ran:50", "ran:10"), resultOrder); // recorded in call order
        assertNotEquals("100", finishOrder.get(0)); // ...even though call 100 did not finish first
    }
}
