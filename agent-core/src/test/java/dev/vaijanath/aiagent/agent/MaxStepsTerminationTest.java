package dev.vaijanath.aiagent.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import dev.vaijanath.aiagent.model.ModelPort;
import dev.vaijanath.aiagent.model.ModelResponse;
import dev.vaijanath.aiagent.model.ToolCall;
import dev.vaijanath.aiagent.tool.Tool;
import dev.vaijanath.aiagent.tool.ToolEffect;
import dev.vaijanath.aiagent.tool.ToolResult;
import dev.vaijanath.aiagent.tool.ToolSpec;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * Infinite-loop protection: a model that never stops requesting tools must be bounded by maxSteps and
 * stop gracefully, not spin forever. This guards the {@code max_steps} termination branch, which the
 * dispatch tests never reach because their scripts finish early.
 */
class MaxStepsTerminationTest {

    @Test
    void stopsWithMaxStepsWhenTheModelNeverFinishes() {
        AtomicInteger modelCalls = new AtomicInteger();
        ModelPort alwaysCallsTool = request -> {
            modelCalls.incrementAndGet();
            return new ModelResponse("", List.of(new ToolCall("c", "loop", "{}")));
        };
        Tool loop = new Tool() {
            @Override
            public ToolSpec spec() {
                return new ToolSpec("loop", "loops", "{\"type\":\"object\"}", ToolEffect.READ_ONLY);
            }

            @Override
            public ToolResult invoke(String argumentsJson) {
                return ToolResult.ok("again");
            }
        };

        int maxSteps = 5;
        AgentResponse r = DefaultAgent.builder()
                .model(alwaysCallsTool)
                .tool(loop)
                .maxSteps(maxSteps)
                .build()
                .run(new AgentRequest("go"));

        assertFalse(r.isCompleted(), "a never-ending tool loop must not report completion");
        assertEquals("max_steps", r.stopReason());
        assertEquals(maxSteps, modelCalls.get(), "the loop must make exactly maxSteps model calls");
    }
}
