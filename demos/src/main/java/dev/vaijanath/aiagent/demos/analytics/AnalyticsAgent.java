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
                        + "names. Steps: (1) call describe_dataset ONCE for the metric/dimension refs, timeColumns, "
                        + "relationships, and the raw tables (names/columns/types); (2) call the right tool: "
                        + "run_query for metrics grouped by dimensions (fan-out-safe joins; 'last N days' via "
                        + "lastDays+timeColumn; 'top N' via limit; simple filters), correlate(target) for 'what "
                        + "correlates with/drives X', or run_sql with read-only DuckDB SQL for anything else — "
                        + "custom filters, time buckets (date_trunc), extract, CASE, window functions, subqueries — "
                        + "using the exact table/column names from describe_dataset; (3) then STOP and answer in "
                        + "plain text using the returned numbers. Never present unfiltered numbers as if filtered. "
                        + "Do not claim causation from correlation.")
                .tool(tools.describeDataset())
                .tool(tools.runQuery())
                .tool(tools.correlate())
                .tool(tools.runSql())
                .maxSteps(8)
                .toolTimeout(Duration.ofMinutes(2))
                .maxToolResultChars(16_000);
        for (AgentObserver observer : observers) {
            builder.observer(observer);
        }
        return builder.build();
    }
}
