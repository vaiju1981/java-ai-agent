package dev.vaijanath.aiagent.demos;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.vaijanath.aiagent.agent.Agent;
import dev.vaijanath.aiagent.agent.AgentRequest;
import dev.vaijanath.aiagent.agent.AgentResponse;
import dev.vaijanath.aiagent.agent.DefaultAgent;
import dev.vaijanath.aiagent.eval.EvalCase;
import dev.vaijanath.aiagent.eval.EvalReport;
import dev.vaijanath.aiagent.eval.Evaluator;
import dev.vaijanath.aiagent.model.Message;
import dev.vaijanath.aiagent.model.ModelPort;
import dev.vaijanath.aiagent.model.ModelRequest;
import dev.vaijanath.aiagent.model.ModelResponse;
import dev.vaijanath.aiagent.model.Role;
import dev.vaijanath.aiagent.model.ToolCall;
import dev.vaijanath.aiagent.model.Usage;
import dev.vaijanath.aiagent.observe.TokenAccountingObserver;
import dev.vaijanath.aiagent.tool.Tool;
import dev.vaijanath.aiagent.tool.ToolApprovers;
import dev.vaijanath.aiagent.tool.ToolEffect;
import dev.vaijanath.aiagent.tool.ToolResult;
import dev.vaijanath.aiagent.tool.ToolSpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Locale;

/**
 * A CSV-to-insights analytics demo: imports a CSV into SQLite and answers profiling, correlation,
 * feature discovery, read-only SQL, and causality-sanity questions through real analytics tools.
 */
public final class AnalyticsInsightAgent {

    private AnalyticsInsightAgent() {}

    public record DemoRun(
            Path csv,
            Path database,
            Path dashboard,
            Path dashboardSpec,
            Path evidence,
            Path decisionLog,
            int dataframeColumns,
            String insightAnswer,
            String sqlAnswer,
            String workbenchAnswer,
            String dashboardUpdateAnswer,
            int rows,
            long modelCalls,
            long totalTokens,
            EvalReport evalReport) {}

    public static void main(String[] args) {
        Path csv = args.length > 0 ? Path.of(args[0]) : sampleCsv();
        DemoRun run = runDemo(csv);

        System.out.println("== AnalyticsInsightAgent ==\n");
        System.out.println("CSV: " + run.csv());
        System.out.println("SQLite: " + run.database());
        System.out.println("Dashboard: " + run.dashboard());
        System.out.println("Dashboard spec: " + run.dashboardSpec());
        System.out.println("Evidence: " + run.evidence());
        System.out.println("Decision log: " + run.decisionLog());
        System.out.println("Rows imported: " + run.rows());
        System.out.println("Tablesaw columns: " + run.dataframeColumns());
        System.out.println("\nInsight question:");
        System.out.println(run.insightAnswer());
        System.out.println("\nSQL question:");
        System.out.println(run.sqlAnswer());
        System.out.println("\nWorkbench question:");
        System.out.println(run.workbenchAnswer());
        System.out.println("\nDashboard update:");
        System.out.println(run.dashboardUpdateAnswer());
        System.out.printf(Locale.ROOT, "%nmodel calls=%d, tokens=%d%n", run.modelCalls(), run.totalTokens());
        System.out.println("\nEval:");
        System.out.print(run.evalReport().render());
    }

    public static DemoRun runDemo(Path csv) {
        Path database = tempFile("java-ai-agent-analytics-", ".sqlite");
        Path dashboard = tempFile("java-ai-agent-dashboard-", ".html");
        Path dashboardSpec = tempFile("java-ai-agent-dashboard-", ".json");
        Path evidence = tempFile("java-ai-agent-evidence-", ".json");
        Path decisionLog = tempFile("java-ai-agent-decision-log-", ".json");
        AnalyticsDatasetConnector.DatasetRef dataset = loadDataset(new LocalCsvDatasetConnector(csv));
        AnalyticsFrame frame = AnalyticsFrame.loadCsv(dataset.localFile());
        AnalyticsSemanticLayer semantic = new AnalyticsSemanticLayer();
        try (AnalyticsDatabase analytics = AnalyticsDatabase.importCsv(database, dataset.localFile())) {
            AnalyticsEvidenceLog evidenceLog = new AnalyticsEvidenceLog(evidence);
            evidenceLog.recordDataset(dataset, analytics.rowCount(), frame.columnCount());
            AnalyticsDecisionLog decisions = new AnalyticsDecisionLog(decisionLog, evidenceLog);
            TokenAccountingObserver tokens = new TokenAccountingObserver();
            Agent agent = buildAgent(analytics, frame, semantic, dashboard, dashboardSpec, evidenceLog, decisions, tokens);

            AgentResponse insights = agent.run(new AgentRequest(
                    "Profile this uploaded CSV. What features look interesting for revenue and conversion_rate? "
                            + "Call out correlations and whether discount_rate causes revenue."));
            AgentResponse sql = agent.run(new AgentRequest(
                    "Using SQL, show average revenue and conversion_rate by channel."));
            AgentResponse workbench = agent.run(new AgentRequest(
                    "Run the full analytics workbench: quality scan, outliers, segment comparisons, "
                            + "hypothesis test, regression, anomaly scan, forecast, chart, and dashboard."));
            AgentResponse dashboardUpdate = agent.run(new AgentRequest(
                    "Update the dashboard with an executive insight tile that flags causal caution and "
                            + "links the supporting evidence."));

            AnalyticsEvidenceLog evalEvidence =
                    new AnalyticsEvidenceLog(tempFile("java-ai-agent-eval-evidence-", ".json"));
            AnalyticsDecisionLog evalDecisions =
                    new AnalyticsDecisionLog(tempFile("java-ai-agent-eval-decisions-", ".json"), evalEvidence);
            EvalReport eval = Evaluator.run(
                    buildAgent(
                            analytics,
                            frame,
                            semantic,
                            tempFile("java-ai-agent-eval-dashboard-", ".html"),
                            tempFile("java-ai-agent-eval-dashboard-", ".json"),
                            evalEvidence,
                            evalDecisions,
                            null),
                    List.of(
                    EvalCase.contains(
                            "correlation-not-causation",
                            "Does discount_rate cause revenue?",
                            "cannot claim causation"),
                    EvalCase.contains(
                            "sql-channel-rollup",
                            "Using SQL, show average revenue and conversion_rate by channel.",
                            "paid_search")));

            return new DemoRun(
                    csv,
                    database,
                    dashboard,
                    dashboardSpec,
                    evidence,
                    decisionLog,
                    frame.columnCount(),
                    insights.output(),
                    sql.output(),
                    workbench.output(),
                    dashboardUpdate.output(),
                    analytics.rowCount(),
                    tokens.modelCalls(),
                    tokens.totalTokens(),
                    eval);
        }
    }

    private static AnalyticsDatasetConnector.DatasetRef loadDataset(AnalyticsDatasetConnector connector) {
        try {
            return connector.load();
        } catch (Exception e) {
            throw new IllegalStateException("failed to load analytics dataset", e);
        }
    }

    private static Agent buildAgent(
            AnalyticsDatabase analytics,
            AnalyticsFrame frame,
            AnalyticsSemanticLayer semantic,
            Path dashboard,
            Path dashboardSpec,
            AnalyticsEvidenceLog evidence,
            AnalyticsDecisionLog decisions,
            TokenAccountingObserver tokens) {
        AnalyticsTools tools = new AnalyticsTools(analytics, frame, semantic, dashboard, dashboardSpec, evidence, decisions);
        DefaultAgent.Builder builder = DefaultAgent.builder()
                .model(new ScriptedAnalyticsModel())
                .systemPrompt("You are a careful analytics agent. Use tools for profiling, correlations, "
                        + "feature scans, causal sanity checks, and read-only SQL. Do not claim causation "
                        + "from observational data.")
                .tool(tools.schema())
                .tool(tools.metricCatalog())
                .tool(tools.semanticQueryPlan())
                .tool(tools.dataframeProfile())
                .tool(tools.profile())
                .tool(tools.columnProfile())
                .tool(tools.dataQuality())
                .tool(tools.outliers())
                .tool(tools.correlations())
                .tool(tools.interestingFeatures())
                .tool(tools.groupBy())
                .tool(tools.pivot())
                .tool(tools.distributionCompare())
                .tool(tools.hypothesisTest())
                .tool(tools.regression())
                .tool(tools.anomalyScan())
                .tool(tools.forecast())
                .tool(tools.chartSpec())
                .tool(tools.dashboardCreate())
                .tool(tools.dashboardUpdate())
                .tool(tools.evidenceManifest())
                .tool(tools.decisionLogWrite())
                .tool(tools.decisionLogRead())
                .tool(tools.causalCheck())
                .tool(tools.sqlQuery())
                .toolApprover(ToolApprovers.denyEffectful("decision_log_write", "dashboard_update"))
                .maxSteps(6)
                .parallelToolCalls(false)
                .toolTimeout(Duration.ofSeconds(10))
                .maxToolResultChars(8_000);
        if (tokens != null) {
            builder.observer(tokens);
        }
        return builder.build();
    }

    private static final class ScriptedAnalyticsModel implements ModelPort {
        @Override
        public ModelResponse chat(ModelRequest request) {
            boolean hasToolResults = request.messages().stream().anyMatch(m -> m.role() == Role.TOOL);
            String lastUser = request.messages().stream()
                    .filter(m -> m.role() == Role.USER)
                    .reduce((a, b) -> b)
                    .map(Message::content)
                    .orElse("")
                    .toLowerCase(Locale.ROOT);
            if (!hasToolResults && lastUser.contains("sql")) {
                return new ModelResponse("", List.of(
                        new ToolCall(
                                "semantic-1",
                                "semantic_query_plan",
                                "{\"question\":\"Using SQL, show average revenue and conversion_rate by channel.\"}"),
                        new ToolCall(
                        "sql-1",
                        "sql_query",
                        "{\"sql\":\"select channel, round(avg(revenue), 2) as avg_revenue, "
                                + "round(avg(conversion_rate), 3) as avg_conversion_rate "
                                        + "from data group by channel order by avg_revenue desc\"}")),
                        new Usage(470, 90));
            }
            if (!hasToolResults && lastUser.contains("workbench")) {
                return new ModelResponse("", List.of(
                        new ToolCall("schema-1", "schema_summary", "{}"),
                        new ToolCall("quality-1", "data_quality_scan", "{}"),
                        new ToolCall("outliers-1", "outlier_scan", "{\"column\":\"revenue\"}"),
                        new ToolCall(
                                "group-1",
                                "groupby_aggregate",
                                "{\"dimension\":\"channel\",\"metric\":\"revenue\",\"aggregation\":\"avg\"}"),
                        new ToolCall(
                                "hypothesis-1",
                                "hypothesis_test",
                                "{\"segment\":\"channel\",\"target\":\"revenue\","
                                        + "\"groupA\":\"paid_search\",\"groupB\":\"social\"}"),
                        new ToolCall(
                                "regression-1",
                                "regression_fit",
                                "{\"target\":\"revenue\",\"features\":[\"visitors\",\"orders\",\"ad_spend\"]}"),
                        new ToolCall("anomaly-1", "anomaly_detection", "{\"column\":\"revenue\"}"),
                        new ToolCall(
                                "forecast-1",
                                "forecast_timeseries",
                                "{\"timeColumn\":\"week\",\"metric\":\"revenue\",\"periods\":4}"),
                        new ToolCall(
                                "chart-1",
                                "chart_spec_generate",
                                "{\"chartType\":\"bar\",\"x\":\"channel\",\"y\":\"revenue\"}"),
                        new ToolCall("dashboard-1", "dashboard_create", "{}"),
                        new ToolCall("evidence-1", "evidence_manifest", "{}"),
                        new ToolCall(
                                "decision-1",
                                "decision_log_write",
                                "{\"owner\":\"analytics-demo-owner\","
                                        + "\"status\":\"proposed\","
                                        + "\"conclusion\":\"Prioritize paid_search and direct revenue review; "
                                        + "do not treat discount_rate as causal without an experiment.\","
                                        + "\"rationale\":\"Workbench evidence links the source dataset, "
                                        + "semantic plan, governed SQL result, and dashboard artifact.\","
                                        + "\"evidenceIds\":[\"evidence-1\",\"evidence-2\","
                                        + "\"evidence-3\",\"evidence-4\"]}")),
                        new Usage(980, 220));
            }
            if (!hasToolResults && lastUser.contains("update the dashboard")) {
                return new ModelResponse("", List.of(new ToolCall(
                        "dashboard-update-1",
                        "dashboard_update",
                        "{\"operation\":\"add_insight_tile\","
                                + "\"tileId\":\"insight-executive-causal\","
                                + "\"title\":\"Executive Decision Guardrail\","
                                + "\"body\":\"Prioritize paid_search and direct revenue review, but do not treat "
                                + "discount_rate as causal without an experiment or stronger causal design.\","
                                + "\"evidenceIds\":[\"evidence-1\",\"evidence-2\","
                                + "\"evidence-3\",\"evidence-4\"]}")),
                        new Usage(420, 95));
            }
            if (!hasToolResults) {
                return new ModelResponse("", List.of(
                        new ToolCall("profile-1", "dataset_profile", "{}"),
                        new ToolCall("corr-1", "correlation_analysis", "{\"target\":\"revenue\"}"),
                        new ToolCall(
                                "features-1",
                                "interesting_features",
                                "{\"target\":\"conversion_rate\"}"),
                        new ToolCall(
                                "causal-1",
                                "causal_sanity_check",
                                "{\"treatment\":\"discount_rate\",\"outcome\":\"revenue\"}")),
                        new Usage(680, 130));
            }
            if (lastUser.contains("sql")) {
                String toolData = toolData(request);
                return ModelResponse.text(
                        "Semantic plan mapped the question to registered revenue and conversion_rate metrics by "
                                + "channel, then executed governed SQL. paid_search and direct are the "
                                + "highest-revenue channels in this sample; the raw grouped rows are: "
                                + summarize(toolData),
                        new Usage(390, 130));
            }
            if (lastUser.contains("workbench")) {
                String toolData = toolData(request);
                return ModelResponse.text(
                        "Workbench completed: schema and data-quality checks ran, revenue was segmented by "
                                + "channel, paid_search was compared with social, a regression feature screen "
                                + "ranked traffic/order drivers, anomaly and forecast passes completed, and a "
                                + "dashboard, evidence manifest, and decision log were generated. Latest evidence: "
                                + summarize(toolData),
                        new Usage(620, 210));
            }
            if (lastUser.contains("update the dashboard")) {
                String toolData = toolData(request);
                return ModelResponse.text(
                        "Dashboard update completed: an executive insight tile was added to the persisted JSON "
                                + "dashboard model, the HTML artifact was re-rendered, and the update was linked "
                                + "to supporting evidence. Latest evidence: " + summarize(toolData),
                        new Usage(360, 140));
            }
            return ModelResponse.text(
                    "Analytics summary: the uploaded CSV was imported into SQLite and profiled before answering. "
                            + "Revenue is most associated with traffic and purchase-intent signals; "
                            + "visitors/orders are the strongest revenue drivers, while conversion_rate is shaped "
                            + "by channel, repeat_customer_share, page_load_ms, and discount_rate. "
                            + "Discount_rate shows an observational relationship with revenue, but the system "
                            + "cannot claim causation from this CSV alone; use an experiment, time design, or "
                            + "causal model before treating it as a lever.",
                    new Usage(520, 190));
        }

        @Override
        public String name() {
            return "demo-analytics-scripted-model";
        }

        private static String toolData(ModelRequest request) {
            return request.messages().stream()
                    .filter(m -> m.role() == Role.TOOL)
                    .reduce((a, b) -> b)
                    .map(Message::content)
                    .orElse("");
        }

        private static String summarize(String toolData) {
            String compact = toolData.replaceAll("\\s+", " ");
            return compact.length() <= 450 ? compact : compact.substring(0, 450) + "...";
        }
    }

    private static final class AnalyticsTools {
        private static final ObjectMapper MAPPER = new ObjectMapper();

        private final AnalyticsDatabase analytics;
        private final AnalyticsFrame frame;
        private final AnalyticsSemanticLayer semantic;
        private final Path dashboard;
        private final Path dashboardSpec;
        private final AnalyticsEvidenceLog evidence;
        private final AnalyticsDecisionLog decisions;

        private AnalyticsTools(
                AnalyticsDatabase analytics,
                AnalyticsFrame frame,
                AnalyticsSemanticLayer semantic,
                Path dashboard,
                Path dashboardSpec,
                AnalyticsEvidenceLog evidence,
                AnalyticsDecisionLog decisions) {
            this.analytics = analytics;
            this.frame = frame;
            this.semantic = semantic;
            this.dashboard = dashboard;
            this.dashboardSpec = dashboardSpec;
            this.evidence = evidence;
            this.decisions = decisions;
        }

        Tool metricCatalog() {
            return readOnly(
                    "metric_catalog",
                    "List registered business metrics and dimensions.",
                    "{}",
                    semantic::catalogJson);
        }

        Tool semanticQueryPlan() {
            return new Tool() {
                @Override
                public ToolSpec spec() {
                    return new ToolSpec(
                            "semantic_query_plan",
                            "Map a natural-language analytics question to metrics, dimensions, grain, and SQL.",
                            "{\"type\":\"object\",\"properties\":{\"question\":{\"type\":\"string\"}},"
                                    + "\"required\":[\"question\"]}",
                            ToolEffect.READ_ONLY);
                }

                @Override
                public ToolResult invoke(String argumentsJson) {
                    String question = text(argumentsJson, "question");
                    String result = semantic.planJson(question);
                    evidence.recordSemanticPlan(question, result);
                    return ToolResult.ok(result);
                }
            };
        }

        Tool dataframeProfile() {
            return readOnly(
                    "dataframe_profile",
                    "Profile the Tablesaw dataframe loaded from the uploaded dataset.",
                    "{}",
                    frame::profileJson);
        }

        Tool schema() {
            return readOnly("schema_summary", "Summarize table schema and inferred column types.",
                    "{}", analytics::schemaJson);
        }

        Tool profile() {
            return readOnly("dataset_profile", "Profile the uploaded dataset: row count, types, missingness, stats.",
                    "{}", frame::profileJson);
        }

        Tool columnProfile() {
            return unaryColumnTool(
                    "column_profile",
                    "Profile one column with quantiles or top values.",
                    "column",
                    analytics::columnProfileJson);
        }

        Tool dataQuality() {
            return readOnly("data_quality_scan", "Scan missingness, duplicate row groups, and cardinality.",
                    "{}", analytics::dataQualityJson);
        }

        Tool outliers() {
            return unaryColumnTool(
                    "outlier_scan",
                    "Detect IQR outliers for one numeric column.",
                    "column",
                    analytics::outliersJson);
        }

        Tool correlations() {
            return new Tool() {
                @Override
                public ToolSpec spec() {
                    return new ToolSpec(
                            "correlation_analysis",
                            "Compute Pearson correlations between numeric columns and a numeric target.",
                            "{\"type\":\"object\",\"properties\":{\"target\":{\"type\":\"string\"}},"
                                    + "\"required\":[\"target\"]}",
                            ToolEffect.READ_ONLY);
                }

                @Override
                public ToolResult invoke(String argumentsJson) {
                    return ToolResult.ok(frame.correlationsJson(text(argumentsJson, "target")));
                }
            };
        }

        Tool interestingFeatures() {
            return new Tool() {
                @Override
                public ToolSpec spec() {
                    return new ToolSpec(
                            "interesting_features",
                            "Rank numeric and categorical features that may explain a numeric target.",
                            "{\"type\":\"object\",\"properties\":{\"target\":{\"type\":\"string\"}},"
                                    + "\"required\":[\"target\"]}",
                            ToolEffect.READ_ONLY);
                }

                @Override
                public ToolResult invoke(String argumentsJson) {
                    return ToolResult.ok(analytics.interestingFeaturesJson(text(argumentsJson, "target")));
                }
            };
        }

        Tool groupBy() {
            return new Tool() {
                @Override
                public ToolSpec spec() {
                    return new ToolSpec(
                            "groupby_aggregate",
                            "Aggregate a metric by a dimension.",
                            "{\"type\":\"object\",\"properties\":{\"dimension\":{\"type\":\"string\"},"
                                    + "\"metric\":{\"type\":\"string\"},\"aggregation\":{\"type\":\"string\"}},"
                                    + "\"required\":[\"dimension\",\"metric\"]}",
                            ToolEffect.READ_ONLY);
                }

                @Override
                public ToolResult invoke(String argumentsJson) {
                    return ToolResult.ok(frame.groupByJson(
                            text(argumentsJson, "dimension"),
                            text(argumentsJson, "metric"),
                            optionalText(argumentsJson, "aggregation", "avg")));
                }
            };
        }

        Tool pivot() {
            return new Tool() {
                @Override
                public ToolSpec spec() {
                    return new ToolSpec(
                            "pivot_table",
                            "Create a pivot summary for a numeric metric.",
                            "{\"type\":\"object\",\"properties\":{\"rowDimension\":{\"type\":\"string\"},"
                                    + "\"columnDimension\":{\"type\":\"string\"},\"metric\":{\"type\":\"string\"}},"
                                    + "\"required\":[\"rowDimension\",\"columnDimension\",\"metric\"]}",
                            ToolEffect.READ_ONLY);
                }

                @Override
                public ToolResult invoke(String argumentsJson) {
                    return ToolResult.ok(analytics.pivotJson(
                            text(argumentsJson, "rowDimension"),
                            text(argumentsJson, "columnDimension"),
                            text(argumentsJson, "metric")));
                }
            };
        }

        Tool distributionCompare() {
            return new Tool() {
                @Override
                public ToolSpec spec() {
                    return new ToolSpec(
                            "distribution_compare",
                            "Compare a numeric target distribution across segment groups.",
                            "{\"type\":\"object\",\"properties\":{\"segment\":{\"type\":\"string\"},"
                                    + "\"target\":{\"type\":\"string\"}},\"required\":[\"segment\",\"target\"]}",
                            ToolEffect.READ_ONLY);
                }

                @Override
                public ToolResult invoke(String argumentsJson) {
                    return ToolResult.ok(analytics.distributionCompareJson(
                            text(argumentsJson, "segment"), text(argumentsJson, "target")));
                }
            };
        }

        Tool hypothesisTest() {
            return new Tool() {
                @Override
                public ToolSpec spec() {
                    return new ToolSpec(
                            "hypothesis_test",
                            "Run an approximate Welch t-test between two segment groups.",
                            "{\"type\":\"object\",\"properties\":{\"segment\":{\"type\":\"string\"},"
                                    + "\"target\":{\"type\":\"string\"},\"groupA\":{\"type\":\"string\"},"
                                    + "\"groupB\":{\"type\":\"string\"}},"
                                    + "\"required\":[\"segment\",\"target\",\"groupA\",\"groupB\"]}",
                            ToolEffect.READ_ONLY);
                }

                @Override
                public ToolResult invoke(String argumentsJson) {
                    return ToolResult.ok(analytics.hypothesisTestJson(
                            text(argumentsJson, "segment"),
                            text(argumentsJson, "target"),
                            text(argumentsJson, "groupA"),
                            text(argumentsJson, "groupB")));
                }
            };
        }

        Tool regression() {
            return new Tool() {
                @Override
                public ToolSpec spec() {
                    return new ToolSpec(
                            "regression_fit",
                            "Fit single-feature linear regression screens against a numeric target.",
                            "{\"type\":\"object\",\"properties\":{\"target\":{\"type\":\"string\"},"
                                    + "\"features\":{\"type\":\"array\",\"items\":{\"type\":\"string\"}}},"
                                    + "\"required\":[\"target\",\"features\"]}",
                            ToolEffect.READ_ONLY);
                }

                @Override
                public ToolResult invoke(String argumentsJson) {
                    return ToolResult.ok(analytics.regressionJson(
                            text(argumentsJson, "target"), stringList(argumentsJson, "features")));
                }
            };
        }

        Tool anomalyScan() {
            return unaryColumnTool(
                    "anomaly_detection",
                    "Detect z-score anomalies for one numeric column.",
                    "column",
                    analytics::anomalyJson);
        }

        Tool forecast() {
            return new Tool() {
                @Override
                public ToolSpec spec() {
                    return new ToolSpec(
                            "forecast_timeseries",
                            "Generate a simple linear trend forecast for a numeric metric.",
                            "{\"type\":\"object\",\"properties\":{\"timeColumn\":{\"type\":\"string\"},"
                                    + "\"metric\":{\"type\":\"string\"},\"periods\":{\"type\":\"integer\"}},"
                                    + "\"required\":[\"timeColumn\",\"metric\"]}",
                            ToolEffect.READ_ONLY);
                }

                @Override
                public ToolResult invoke(String argumentsJson) {
                    return ToolResult.ok(analytics.forecastJson(
                            text(argumentsJson, "timeColumn"),
                            text(argumentsJson, "metric"),
                            optionalInt(argumentsJson, "periods", 4)));
                }
            };
        }

        Tool chartSpec() {
            return new Tool() {
                @Override
                public ToolSpec spec() {
                    return new ToolSpec(
                            "chart_spec_generate",
                            "Generate a Vega-Lite chart spec for two fields.",
                            "{\"type\":\"object\",\"properties\":{\"chartType\":{\"type\":\"string\"},"
                                    + "\"x\":{\"type\":\"string\"},\"y\":{\"type\":\"string\"}},"
                                    + "\"required\":[\"x\",\"y\"]}",
                            ToolEffect.READ_ONLY);
                }

                @Override
                public ToolResult invoke(String argumentsJson) {
                    return ToolResult.ok(frame.chartSpecJson(
                            optionalText(argumentsJson, "chartType", "bar"),
                            text(argumentsJson, "x"),
                            text(argumentsJson, "y")));
                }
            };
        }

        Tool dashboardCreate() {
            return new Tool() {
                @Override
                public ToolSpec spec() {
                    return new ToolSpec(
                            "dashboard_create",
                            "Create an HTML dashboard artifact for the imported dataset.",
                            "{}",
                            ToolEffect.READ_ONLY);
                }

                @Override
                public ToolResult invoke(String argumentsJson) {
                    AnalyticsDashboard.DashboardArtifact artifact =
                            AnalyticsDashboard.create(analytics, frame, dashboardSpec, dashboard);
                    evidence.recordDashboard(artifact.html(), artifact.spec());
                    return ToolResult.ok("{\"dashboard\":\"" + artifact.html() + "\",\"dashboardSpec\":\""
                            + artifact.spec() + "\"}");
                }
            };
        }

        Tool dashboardUpdate() {
            return new Tool() {
                @Override
                public ToolSpec spec() {
                    return new ToolSpec(
                            "dashboard_update",
                            "Update the persisted dashboard JSON model and re-render its HTML artifact.",
                            "{\"type\":\"object\",\"properties\":{\"operation\":{\"type\":\"string\"},"
                                    + "\"tileId\":{\"type\":\"string\"},\"title\":{\"type\":\"string\"},"
                                    + "\"body\":{\"type\":\"string\"},"
                                    + "\"evidenceIds\":{\"type\":\"array\",\"items\":{\"type\":\"string\"}}},"
                                    + "\"required\":[\"operation\",\"tileId\"]}",
                            ToolEffect.EFFECTFUL);
                }

                @Override
                public ToolResult invoke(String argumentsJson) {
                    AnalyticsDashboard.DashboardUpdate update = AnalyticsDashboard.update(
                            dashboardSpec,
                            dashboard,
                            text(argumentsJson, "operation"),
                            text(argumentsJson, "tileId"),
                            optionalText(argumentsJson, "title", ""),
                            optionalText(argumentsJson, "body", ""),
                            optionalStringList(argumentsJson, "evidenceIds"));
                    evidence.recordDashboardUpdate(
                            update.html(), update.spec(), update.operation(), update.tileId(), update.revision());
                    return ToolResult.ok("{\"dashboard\":\"" + update.html() + "\",\"dashboardSpec\":\""
                            + update.spec() + "\",\"operation\":\"" + update.operation()
                            + "\",\"tileId\":\"" + update.tileId() + "\",\"revision\":"
                            + update.revision() + "}");
                }
            };
        }

        Tool evidenceManifest() {
            return readOnly(
                    "evidence_manifest",
                    "Return the persisted lineage and evidence manifest for the current analysis.",
                    "{}",
                    evidence::manifestJson);
        }

        Tool decisionLogWrite() {
            return new Tool() {
                @Override
                public ToolSpec spec() {
                    return new ToolSpec(
                            "decision_log_write",
                            "Persist an analyst decision tied to evidence ids, owner, status, and rationale.",
                            "{\"type\":\"object\",\"properties\":{\"owner\":{\"type\":\"string\"},"
                                    + "\"conclusion\":{\"type\":\"string\"},\"status\":{\"type\":\"string\"},"
                                    + "\"rationale\":{\"type\":\"string\"},"
                                    + "\"evidenceIds\":{\"type\":\"array\",\"items\":{\"type\":\"string\"}}},"
                                    + "\"required\":[\"owner\",\"conclusion\",\"evidenceIds\"]}",
                            ToolEffect.EFFECTFUL);
                }

                @Override
                public ToolResult invoke(String argumentsJson) {
                    String result = decisions.write(
                            text(argumentsJson, "owner"),
                            text(argumentsJson, "conclusion"),
                            optionalText(argumentsJson, "status", "proposed"),
                            optionalText(argumentsJson, "rationale", ""),
                            stringList(argumentsJson, "evidenceIds"));
                    return ToolResult.ok(result);
                }
            };
        }

        Tool decisionLogRead() {
            return readOnly(
                    "decision_log_read",
                    "Read persisted analyst decisions and their linked evidence ids.",
                    "{}",
                    decisions::decisionsJson);
        }

        Tool causalCheck() {
            return new Tool() {
                @Override
                public ToolSpec spec() {
                    return new ToolSpec(
                            "causal_sanity_check",
                            "Check whether an observed relationship supports a causal claim.",
                            "{\"type\":\"object\",\"properties\":{\"treatment\":{\"type\":\"string\"},"
                                    + "\"outcome\":{\"type\":\"string\"}},"
                                    + "\"required\":[\"treatment\",\"outcome\"]}",
                            ToolEffect.READ_ONLY);
                }

                @Override
                public ToolResult invoke(String argumentsJson) {
                    return ToolResult.ok(analytics.causalCheckJson(
                            text(argumentsJson, "treatment"), text(argumentsJson, "outcome")));
                }
            };
        }

        Tool sqlQuery() {
            return new Tool() {
                @Override
                public ToolSpec spec() {
                    return new ToolSpec(
                            "sql_query",
                            "Run a read-only SELECT query against the imported CSV table named data.",
                            "{\"type\":\"object\",\"properties\":{\"sql\":{\"type\":\"string\"}},"
                                    + "\"required\":[\"sql\"]}",
                            ToolEffect.READ_ONLY);
                }

                @Override
                public ToolResult invoke(String argumentsJson) {
                    String sql = text(argumentsJson, "sql");
                    String result = analytics.sqlJson(sql);
                    evidence.recordQuery(sql, result);
                    return ToolResult.ok(result);
                }
            };
        }

        private Tool readOnly(String name, String description, String schema, ResultSupplier supplier) {
            return new Tool() {
                @Override
                public ToolSpec spec() {
                    return new ToolSpec(name, description, schema, ToolEffect.READ_ONLY);
                }

                @Override
                public ToolResult invoke(String argumentsJson) {
                    return ToolResult.ok(supplier.get());
                }
            };
        }

        private Tool unaryColumnTool(String name, String description, String field, ColumnFunction function) {
            return new Tool() {
                @Override
                public ToolSpec spec() {
                    return new ToolSpec(
                            name,
                            description,
                            "{\"type\":\"object\",\"properties\":{\"" + field + "\":{\"type\":\"string\"}},"
                                    + "\"required\":[\"" + field + "\"]}",
                            ToolEffect.READ_ONLY);
                }

                @Override
                public ToolResult invoke(String argumentsJson) {
                    return ToolResult.ok(function.apply(text(argumentsJson, field)));
                }
            };
        }

        private static String text(String argumentsJson, String field) {
            JsonNode node = parse(argumentsJson).get(field);
            if (node == null || node.asText().isBlank()) {
                throw new IllegalArgumentException("missing required field: " + field);
            }
            return node.asText();
        }

        private static String optionalText(String argumentsJson, String field, String fallback) {
            JsonNode node = parse(argumentsJson).get(field);
            return node == null || node.asText().isBlank() ? fallback : node.asText();
        }

        private static int optionalInt(String argumentsJson, String field, int fallback) {
            JsonNode node = parse(argumentsJson).get(field);
            return node == null || !node.canConvertToInt() ? fallback : node.asInt();
        }

        private static List<String> stringList(String argumentsJson, String field) {
            JsonNode node = parse(argumentsJson).get(field);
            if (node == null || !node.isArray()) {
                throw new IllegalArgumentException("missing required array field: " + field);
            }
            List<String> values = new java.util.ArrayList<>();
            node.forEach(item -> values.add(item.asText()));
            return values;
        }

        private static List<String> optionalStringList(String argumentsJson, String field) {
            JsonNode node = parse(argumentsJson).get(field);
            if (node == null || !node.isArray()) {
                return List.of();
            }
            List<String> values = new java.util.ArrayList<>();
            node.forEach(item -> values.add(item.asText()));
            return values;
        }

        private static JsonNode parse(String argumentsJson) {
            try {
                return MAPPER.readTree(argumentsJson);
            } catch (IOException e) {
                throw new IllegalArgumentException("invalid tool arguments JSON", e);
            }
        }

        private interface ResultSupplier {
            String get();
        }

        private interface ColumnFunction {
            String apply(String column);
        }
    }

    static Path sampleCsv() {
        Path csv = tempFile("java-ai-agent-marketing-", ".csv");
        String data = """
                week,channel,visitors,ad_spend,discount_rate,page_load_ms,repeat_customer_share,orders,revenue,conversion_rate
                1,paid_search,12000,24000,0.05,820,0.28,690,124200,0.057
                2,paid_search,13500,26500,0.08,790,0.30,805,148925,0.060
                3,paid_search,14200,28000,0.10,760,0.31,910,172900,0.064
                4,paid_search,15100,31000,0.12,740,0.33,1010,196950,0.067
                5,direct,9800,2500,0.02,610,0.52,705,137475,0.072
                6,direct,10200,2600,0.03,590,0.54,765,151470,0.075
                7,direct,10800,2700,0.04,570,0.56,842,169242,0.078
                8,direct,11100,2800,0.04,560,0.57,888,181040,0.080
                9,email,5400,1800,0.15,640,0.61,475,80750,0.088
                10,email,5900,1900,0.18,630,0.62,548,95800,0.093
                11,email,6200,2100,0.20,620,0.64,601,108180,0.097
                12,email,6600,2200,0.22,610,0.65,667,122728,0.101
                13,social,8800,15500,0.06,980,0.19,308,47740,0.035
                14,social,9100,16200,0.08,1030,0.20,337,53852,0.037
                15,social,9500,17100,0.10,1080,0.21,371,61215,0.039
                16,social,9900,18400,0.12,1120,0.22,416,70720,0.042
                """;
        try {
            Files.writeString(csv, data, StandardCharsets.UTF_8);
            return csv;
        } catch (IOException e) {
            throw new IllegalStateException("failed to write sample analytics CSV", e);
        }
    }

    private static Path tempFile(String prefix, String suffix) {
        try {
            return Files.createTempFile(prefix, suffix);
        } catch (IOException e) {
            throw new IllegalStateException("failed to create temp file", e);
        }
    }
}
