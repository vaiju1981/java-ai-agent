package dev.vaijanath.aiagent.reference;

import dev.vaijanath.aiagent.agent.AgentResponse;
import dev.vaijanath.aiagent.model.ModelResponse;
import dev.vaijanath.aiagent.observe.AgentObserver;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * Bridges agent lifecycle events to Micrometer meters, exposed via {@code /actuator/prometheus}: turn
 * outcomes, model-call counts, and token usage. It is wired through {@code ProductionAgentRuntime}'s
 * redacting observer path, so it only ever sees metering metadata — never message content.
 */
class MeterRegistryAgentObserver implements AgentObserver {

    private final MeterRegistry registry;

    MeterRegistryAgentObserver(MeterRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void onModelResponse(ModelResponse response) {
        registry.counter("agent.model.calls").increment();
        registry.counter("agent.tokens", "direction", "input").increment(response.usage().inputTokens());
        registry.counter("agent.tokens", "direction", "output").increment(response.usage().outputTokens());
    }

    @Override
    public void onTurnEnd(AgentResponse response) {
        String outcome = response.blocked() ? "blocked" : response.stopReason();
        registry.counter("agent.turns", "outcome", outcome).increment();
    }

    @Override
    public void onError(String stage, Throwable error) {
        registry.counter("agent.errors", "stage", stage).increment();
    }
}
