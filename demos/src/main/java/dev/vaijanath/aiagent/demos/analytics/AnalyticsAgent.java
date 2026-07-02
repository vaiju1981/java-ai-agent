package dev.vaijanath.aiagent.demos.analytics;

import dev.vaijanath.aiagent.agent.Agent;
import dev.vaijanath.aiagent.agent.DefaultAgent;
import dev.vaijanath.aiagent.model.ModelPort;
import dev.vaijanath.aiagent.observe.AgentObserver;
import java.time.Duration;

/**
 * Wires a schema-driven analytics agent: a configured {@link ModelPort} brain plus the generic
 * read-only tools. The model is injected (config selects Ollama / Anthropic / OpenAI upstream), so
 * this class stays provider-agnostic and has no dataset-specific knowledge.
 */
final class AnalyticsAgent {

    private AnalyticsAgent() {}

    static Agent create(CsvSource source, ModelPort model, AgentObserver... observers) {
        AnalyticsToolset tools = new AnalyticsToolset(source);
        DefaultAgent.Builder builder = DefaultAgent.builder()
                .model(model)
                .systemPrompt("You are a careful data analyst over an arbitrary dataset; never assume column "
                        + "names. Follow exactly three steps: (1) call describe_dataset ONCE to get the exact "
                        + "metric/dimension 'ref' values; (2) call run_query ONCE with the correct refs; (3) then "
                        + "STOP calling tools and reply with a short plain-text answer using the numbers from "
                        + "run_query. Never call a tool again once you have the query results. Do not claim "
                        + "causation from correlation.")
                .tool(tools.describeDataset())
                .tool(tools.runQuery())
                .maxSteps(8)
                .toolTimeout(Duration.ofMinutes(2))
                .maxToolResultChars(8_000);
        for (AgentObserver observer : observers) {
            builder.observer(observer);
        }
        return builder.build();
    }
}
