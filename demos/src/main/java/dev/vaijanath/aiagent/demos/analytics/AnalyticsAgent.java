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
                .systemPrompt("You are a careful data analyst over this dataset. Use these EXACT refs.\n"
                        + "SCHEMA: " + tools.catalogJson() + "\n"
                        + "Answer each question by calling ONE tool, then replying in plain text with the numbers:\n"
                        + "- run_query: metrics grouped by dimensions; 'last N days' -> lastDays + timeColumn; "
                        + "'top N' -> limit; simple filters {column,op,value}. Cross-table joins are fan-out-safe.\n"
                        + "- summarize(metric): distribution/percentiles of one measure.\n"
                        + "- correlate(target): 'what correlates with / drives' a metric.\n"
                        + "- outliers(metric): unusual values by z-score.\n"
                        + "- regression(target): which measures linearly predict a target.\n"
                        + "- run_sql: read-only DuckDB SQL for anything else — custom filters, time buckets "
                        + "(date_trunc; a time column's encoding is shown, e.g. EPOCH_MILLIS -> to_timestamp(col/1000)), "
                        + "window functions, subqueries.\n"
                        + "You already have the schema above, so describe_dataset is rarely needed. After the tool "
                        + "returns, STOP calling tools and answer. Never present unfiltered numbers as if filtered; "
                        + "do not claim causation from correlation.")
                .tool(tools.describeDataset())
                .tool(tools.runQuery())
                .tool(tools.correlate())
                .tool(tools.summarize())
                .tool(tools.outliers())
                .tool(tools.regression())
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
