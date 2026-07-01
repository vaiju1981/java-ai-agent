package dev.vaijanath.aiagent.demos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AnalyticsInsightAgentTest {

    @TempDir
    Path tempDir;

    @Test
    void demoImportsCsvAndAnswersAnalyticsQuestions() throws Exception {
        AnalyticsInsightAgent.DemoRun run = AnalyticsInsightAgent.runDemo(AnalyticsInsightAgent.sampleCsv());

        assertEquals(16, run.rows());
        assertEquals(10, run.dataframeColumns());
        assertTrue(run.insightAnswer().contains("cannot claim causation"));
        assertTrue(run.insightAnswer().contains("Revenue is most associated"));
        assertTrue(run.sqlAnswer().contains("Semantic plan mapped"));
        assertTrue(run.sqlAnswer().contains("paid_search"));
        assertTrue(run.workbenchAnswer().contains("Workbench completed"));
        assertTrue(run.workbenchAnswer().contains("decision log"));
        assertTrue(run.dashboardUpdateAnswer().contains("Dashboard update completed"));
        assertTrue(Files.exists(run.dashboard()));
        assertFalse(Files.readString(run.dashboard()).isBlank());
        assertTrue(Files.exists(run.dashboardSpec()));
        String dashboardSpec = Files.readString(run.dashboardSpec());
        assertTrue(dashboardSpec.contains("\"filters\""));
        assertTrue(dashboardSpec.contains("\"layout\""));
        assertTrue(dashboardSpec.contains("\"type\" : \"kpi\""));
        assertTrue(dashboardSpec.contains("\"type\" : \"chart\""));
        assertTrue(dashboardSpec.contains("\"type\" : \"table\""));
        assertTrue(dashboardSpec.contains("\"drilldown\""));
        assertTrue(dashboardSpec.contains("Inspect channel rows"));
        assertTrue(dashboardSpec.contains("\"revision\" : 2"));
        assertTrue(dashboardSpec.contains("\"changeLog\""));
        assertTrue(dashboardSpec.contains("insight-executive-causal"));
        assertTrue(dashboardSpec.contains("Executive Decision Guardrail"));
        assertTrue(Files.exists(run.evidence()));
        String evidence = Files.readString(run.evidence());
        assertTrue(evidence.contains("\"type\" : \"dataset\""));
        assertTrue(evidence.contains("\"type\" : \"semantic_plan\""));
        assertTrue(evidence.contains("\"type\" : \"query\""));
        assertTrue(evidence.contains("\"type\" : \"artifact\""));
        assertTrue(evidence.contains("\"type\" : \"artifact_update\""));
        assertTrue(evidence.contains("dashboard_create"));
        assertTrue(evidence.contains("dashboard_update"));
        assertTrue(evidence.contains(run.dashboardSpec().toAbsolutePath().toString()));
        assertTrue(Files.exists(run.decisionLog()));
        String decisions = Files.readString(run.decisionLog());
        assertTrue(decisions.contains("\"decisionCount\" : 1"));
        assertTrue(decisions.contains("\"owner\" : \"analytics-demo-owner\""));
        assertTrue(decisions.contains("\"status\" : \"proposed\""));
        assertTrue(decisions.contains("do not treat discount_rate as causal"));
        assertTrue(decisions.contains(run.evidence().toAbsolutePath().toString()));
        assertTrue(decisions.contains("\"evidence-4\""));
        assertTrue(run.modelCalls() > 0);
        assertTrue(run.totalTokens() > 0);
        assertEquals(2, run.evalReport().passed());
    }

    @Test
    void demoAcceptsCallerProvidedCsv() throws Exception {
        Path csv = tempDir.resolve("uploaded.csv");
        Files.writeString(csv, """
                channel,visitors,orders,revenue,conversion_rate,discount_rate
                direct,100,10,1000,0.10,0.02
                direct,120,14,1500,0.12,0.03
                paid_search,200,20,2500,0.10,0.08
                paid_search,260,32,4300,0.12,0.10
                """, StandardCharsets.UTF_8);

        AnalyticsInsightAgent.DemoRun run = AnalyticsInsightAgent.runDemo(csv);

        assertEquals(4, run.rows());
        assertEquals(6, run.dataframeColumns());
        assertTrue(run.sqlAnswer().contains("paid_search"));
    }

    @Test
    void tablesawProfileIncludesColumnStatistics() {
        AnalyticsFrame frame = AnalyticsFrame.loadCsv(AnalyticsInsightAgent.sampleCsv());

        String profile = frame.profileJson();

        assertTrue(profile.contains("\"engine\":\"tablesaw\""));
        assertTrue(profile.contains("\"rows\":16"));
        assertTrue(profile.contains("\"name\":\"revenue\""));
        assertTrue(profile.contains("\"mean\""));
        assertTrue(profile.contains("\"topValues\""));
    }

    @Test
    void tablesawComputesCorrelationsAndGroupBy() {
        AnalyticsFrame frame = AnalyticsFrame.loadCsv(AnalyticsInsightAgent.sampleCsv());

        String correlations = frame.correlationsJson("revenue");
        String grouped = frame.groupByJson("channel", "revenue", "avg");
        String chart = frame.chartSpecJson("bar", "channel", "revenue");

        assertTrue(correlations.contains("\"engine\":\"tablesaw\""));
        assertTrue(correlations.contains("\"visitors\""));
        assertTrue(grouped.contains("paid_search"));
        assertTrue(chart.contains("Tablesaw-prepared chart"));
    }

    @Test
    void semanticLayerPlansMetricQueries() {
        AnalyticsSemanticLayer semantic = new AnalyticsSemanticLayer();

        String catalog = semantic.catalogJson();
        String plan = semantic.planJson("Show average revenue and conversion_rate by channel.");

        assertTrue(catalog.contains("\"revenue\""));
        assertTrue(catalog.contains("\"channel\""));
        assertTrue(plan.contains("\"metrics\""));
        assertTrue(plan.contains("avg_revenue"));
        assertTrue(plan.contains("conversion_rate"));
        assertTrue(plan.contains("group by channel"));
    }
}
