package dev.vaijanath.aiagent.springboot.metrics;

import dev.vaijanath.aiagent.agent.AgentResponse;
import dev.vaijanath.aiagent.model.ModelResponse;
import dev.vaijanath.aiagent.model.ToolCall;
import dev.vaijanath.aiagent.observe.AgentObserver;
import dev.vaijanath.aiagent.tool.ToolResult;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.Objects;

/**
 * Bridges agent lifecycle events to Micrometer meters, exposed via {@code /actuator/prometheus}: model
 * calls, token usage, tool calls and their outcomes, turn outcomes, and errors by stage. It reads only
 * metering metadata (counts, token totals, tool names, stop reasons) — never message content — so it is
 * safe to wire through the governed runtime's redacting-observer path.
 *
 * <p>Tags are low-cardinality by construction: directions ({@code input}/{@code output}), tool names
 * (developer-defined and bounded), outcomes ({@code ok}/{@code error} and stop reasons), and pipeline
 * stages. Alongside the counters it records agent-domain latency timers — model call, tool call, and
 * whole turn — which (unlike Actuator's {@code http.server.requests}) also cover non-HTTP agents and
 * let you see where a turn's time goes.
 */
public final class MicrometerAgentObserver implements AgentObserver {

    private final MeterRegistry registry;

    public MicrometerAgentObserver(MeterRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry");
    }

    @Override
    public void onModelResponse(ModelResponse response) {
        registry.counter("agent.model.calls").increment();
        registry.counter("agent.tokens", "direction", "input").increment(response.usage().inputTokens());
        registry.counter("agent.tokens", "direction", "output").increment(response.usage().outputTokens());
    }

    @Override
    public void onModelResponse(ModelResponse response, Duration latency) {
        registry.timer("agent.model.latency").record(latency);
        onModelResponse(response);
    }

    @Override
    public void onToolCall(ToolCall call) {
        registry.counter("agent.tool.calls", "tool", call.name()).increment();
    }

    @Override
    public void onToolResult(String toolName, ToolResult result) {
        registry.counter("agent.tool.results", "tool", toolName, "outcome", result.error() ? "error" : "ok")
                .increment();
    }

    @Override
    public void onToolResult(String toolName, ToolResult result, Duration latency) {
        registry.timer("agent.tool.latency", "tool", toolName).record(latency);
        onToolResult(toolName, result);
    }

    @Override
    public void onTurnEnd(AgentResponse response) {
        String outcome = response.blocked() ? "blocked" : response.stopReason();
        registry.counter("agent.turns", "outcome", outcome).increment();
    }

    @Override
    public void onTurnEnd(AgentResponse response, Duration duration) {
        registry.timer("agent.turn.latency").record(duration);
        onTurnEnd(response);
    }

    @Override
    public void onError(String stage, Throwable error) {
        registry.counter("agent.errors", "stage", stage).increment();
    }
}
