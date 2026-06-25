package dev.vaijanath.aiagent.agent;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vaijanath.aiagent.model.ModelPort;
import dev.vaijanath.aiagent.model.ModelResponse;
import dev.vaijanath.aiagent.model.ToolCall;
import dev.vaijanath.aiagent.observe.AgentObserver;
import dev.vaijanath.aiagent.tool.Tool;
import dev.vaijanath.aiagent.tool.ToolEffect;
import dev.vaijanath.aiagent.tool.ToolResult;
import dev.vaijanath.aiagent.tool.ToolSpec;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class ObserverTimingTest {

    @Test
    void modelAndTurnLatenciesAreReported() {
        AtomicReference<Duration> modelLatency = new AtomicReference<>();
        AtomicReference<Duration> turnLatency = new AtomicReference<>();
        AgentObserver capture = new AgentObserver() {
            @Override
            public void onModelResponse(ModelResponse response, Duration latency) {
                modelLatency.set(latency);
            }

            @Override
            public void onTurnEnd(dev.vaijanath.aiagent.agent.AgentResponse response, Duration duration) {
                turnLatency.set(duration);
            }
        };

        DefaultAgent.builder()
                .model(req -> ModelResponse.text("hi"))
                .observer(capture)
                .build()
                .run(new AgentRequest("go"));

        assertNotNull(modelLatency.get(), "model latency was reported");
        assertFalse(modelLatency.get().isNegative());
        assertNotNull(turnLatency.get(), "turn latency was reported");
        assertFalse(turnLatency.get().isNegative());
    }

    @Test
    void toolLatencyIsReported() {
        AtomicReference<Duration> toolLatency = new AtomicReference<>();
        AgentObserver capture = new AgentObserver() {
            @Override
            public void onToolResult(String toolName, ToolResult result, Duration latency) {
                toolLatency.set(latency);
            }
        };
        ModelPort callsOnce = new ModelPort() {
            private int n = 0;

            @Override
            public ModelResponse chat(dev.vaijanath.aiagent.model.ModelRequest request) {
                return ++n == 1
                        ? new ModelResponse("", List.of(new ToolCall("c1", "noop", "{}")))
                        : ModelResponse.text("done");
            }
        };

        DefaultAgent.builder().model(callsOnce).tool(noop()).observer(capture).build()
                .run(new AgentRequest("go"));

        assertNotNull(toolLatency.get(), "tool latency was reported");
        assertFalse(toolLatency.get().isNegative());
    }

    @Test
    void anUntimedObserverStillReceivesEvents() {
        // Backward-compat: the runtime calls the timed variants, which default to the untimed ones.
        AtomicBoolean sawModel = new AtomicBoolean(false);
        AgentObserver legacy = new AgentObserver() {
            @Override
            public void onModelResponse(ModelResponse response) {
                sawModel.set(true);
            }
        };

        DefaultAgent.builder().model(req -> ModelResponse.text("hi")).observer(legacy).build()
                .run(new AgentRequest("go"));

        assertTrue(sawModel.get(), "an observer overriding only the untimed callback still fires");
    }

    private static Tool noop() {
        return new Tool() {
            @Override
            public ToolSpec spec() {
                return new ToolSpec("noop", "no-op", "{\"type\":\"object\",\"properties\":{}}", ToolEffect.READ_ONLY);
            }

            @Override
            public ToolResult invoke(String argumentsJson) {
                return ToolResult.ok("ok");
            }
        };
    }
}
