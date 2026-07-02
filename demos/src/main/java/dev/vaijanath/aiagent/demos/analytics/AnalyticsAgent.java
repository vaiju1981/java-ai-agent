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

    static Agent create(DataSource source, ModelPort model, AgentObserver... observers) {
        return create(source, SemanticModel.fromProfile(DatasetProfiler.profile(source)), model, observers);
    }

    static Agent create(DataSource source, SemanticModel semantic, ModelPort model, AgentObserver... observers) {
        AnalyticsToolset tools = new AnalyticsToolset(source, semantic);
        DefaultAgent.Builder builder = DefaultAgent.builder()
                .model(model)
                .systemPrompt("You are a careful data analyst over an arbitrary dataset; never assume column "
                        + "names. Steps: (1) call describe_dataset ONCE for the exact metric/dimension refs, the "
                        + "timeColumns, and relationships; (2) call run_query with the correct refs — for a period "
                        + "like 'last N days' set lastDays=N and timeColumn to a ref from timeColumns; use filters "
                        + "for other constraints; (3) then STOP calling tools and answer in plain text using the "
                        + "returned numbers. If a requested time window or filter cannot be applied with the "
                        + "available columns, SAY SO plainly — never present unfiltered numbers as if they were "
                        + "filtered. Do not claim causation from correlation.")
                .tool(tools.describeDataset())
                .tool(tools.runQuery())
                .maxSteps(8)
                .toolTimeout(Duration.ofMinutes(2))
                .maxToolResultChars(16_000);
        for (AgentObserver observer : observers) {
            builder.observer(observer);
        }
        return builder.build();
    }
}
