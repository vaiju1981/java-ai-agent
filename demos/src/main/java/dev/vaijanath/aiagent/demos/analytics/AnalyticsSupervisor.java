package dev.vaijanath.aiagent.demos.analytics;

import dev.vaijanath.aiagent.agent.Agent;
import dev.vaijanath.aiagent.agent.DefaultAgent;
import dev.vaijanath.aiagent.model.ModelPort;
import dev.vaijanath.aiagent.supervise.LlmRouter;
import dev.vaijanath.aiagent.supervise.Router;
import dev.vaijanath.aiagent.supervise.SupervisorAgent;
import java.time.Duration;

/**
 * Multi-agent composition: a {@link SupervisorAgent} routes each request to the right specialist —
 * an "analyst" that computes numbers via the query tools, or a "schema_guide" that explains what
 * data exists. Both share the same generated model; only their tools and instructions differ.
 */
final class AnalyticsSupervisor {

    private AnalyticsSupervisor() {}

    static Agent create(DataSource source, SemanticModel semantic, ModelPort model) {
        return create(source, semantic, model, new LlmRouter(model));
    }

    static Agent create(DataSource source, SemanticModel semantic, ModelPort model, Router router) {
        AnalyticsToolset tools = new AnalyticsToolset(source, semantic);
        Agent analyst = AnalyticsAgent.create(source, semantic, model);
        Agent schemaGuide = DefaultAgent.builder()
                .model(model)
                .systemPrompt("You explain the dataset's structure — its tables, columns, types, and "
                        + "relationships — in plain English. Call describe_dataset once, then summarize what data "
                        + "is available. Do not compute metrics.")
                .tool(tools.describeDataset())
                .maxSteps(4)
                .toolTimeout(Duration.ofMinutes(2))
                .maxToolResultChars(16_000)
                .build();
        return SupervisorAgent.builder()
                .specialist("analyst",
                        "Answers questions that need numbers: metrics, aggregations, comparisons, rankings.",
                        analyst)
                .specialist("schema_guide",
                        "Explains what data exists: tables, columns, types, and relationships.",
                        schemaGuide)
                .router(router)
                .fallback("analyst")
                .build();
    }
}
