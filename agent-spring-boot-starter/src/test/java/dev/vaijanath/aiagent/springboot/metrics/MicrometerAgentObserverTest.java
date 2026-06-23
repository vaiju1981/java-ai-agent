package dev.vaijanath.aiagent.springboot.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import dev.vaijanath.aiagent.agent.AgentResponse;
import dev.vaijanath.aiagent.model.ModelResponse;
import dev.vaijanath.aiagent.model.ToolCall;
import dev.vaijanath.aiagent.model.Usage;
import dev.vaijanath.aiagent.tool.ToolResult;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

class MicrometerAgentObserverTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final MicrometerAgentObserver observer = new MicrometerAgentObserver(registry);

    @Test
    void metersModelCallsAndTokenUsage() {
        observer.onModelResponse(ModelResponse.text("hi", new Usage(10, 4)));

        assertThat(registry.counter("agent.model.calls").count()).isEqualTo(1.0);
        assertThat(registry.counter("agent.tokens", "direction", "input").count()).isEqualTo(10.0);
        assertThat(registry.counter("agent.tokens", "direction", "output").count()).isEqualTo(4.0);
    }

    @Test
    void metersToolCallsAndResultsTaggedByOutcome() {
        observer.onToolCall(new ToolCall("id1", "guidance_search", "{}"));
        observer.onToolResult("guidance_search", ToolResult.ok("found"));
        observer.onToolResult("guidance_search", ToolResult.error("boom"));

        assertThat(registry.counter("agent.tool.calls", "tool", "guidance_search").count()).isEqualTo(1.0);
        assertThat(registry.counter("agent.tool.results", "tool", "guidance_search", "outcome", "ok").count())
                .isEqualTo(1.0);
        assertThat(registry.counter("agent.tool.results", "tool", "guidance_search", "outcome", "error").count())
                .isEqualTo(1.0);
    }

    @Test
    void metersTurnOutcomeAndErrorsByStage() {
        AgentResponse response = AgentResponse.completed("done");
        observer.onTurnEnd(response);
        observer.onError("model", new RuntimeException("x"));

        String outcome = response.blocked() ? "blocked" : response.stopReason();
        assertThat(registry.counter("agent.turns", "outcome", outcome).count()).isEqualTo(1.0);
        assertThat(registry.counter("agent.errors", "stage", "model").count()).isEqualTo(1.0);
    }
}
