package dev.vaijanath.aiagent.demos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AnalyticsDashboardServerTest {

    @TempDir
    Path tempDir;

    @Test
    void startsWithEmptyWorkspaceUntilCsvIsUploaded() throws Exception {
        try (AnalyticsDashboardServer server = AnalyticsDashboardServer.startEmpty(tempDir.resolve("empty-workspace"))) {
            HttpClient client = HttpClient.newHttpClient();
            URI root = server.uri();

            HttpResponse<String> session = client.send(
                    HttpRequest.newBuilder(root.resolve("/api/session")).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(200, session.statusCode());
            assertTrue(session.body().contains("\"rows\" : 0"));
            assertTrue(session.body().contains("\"csv\" : \"\""));

            HttpResponse<String> schema = client.send(
                    HttpRequest.newBuilder(root.resolve("/api/schema")).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(200, schema.statusCode());
            assertTrue(schema.body().contains("\"rows\" : 0"));
            assertTrue(schema.body().contains("\"columns\" : [ ]"));

            HttpResponse<String> connectors = client.send(
                    HttpRequest.newBuilder(root.resolve("/api/connectors")).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(200, connectors.statusCode());
            assertTrue(connectors.body().contains("No dataset loaded"));
            assertTrue(connectors.body().contains("\"rawDataSentToModel\" : false"));

            HttpRequest chat = HttpRequest.newBuilder(root.resolve("/api/chat"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(
                            "{\"question\":\"What features are important for revenue?\"}"))
                    .build();
            HttpResponse<String> emptyChat = client.send(chat, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, emptyChat.statusCode());
            assertTrue(emptyChat.body().contains("Upload or connect a dataset"));

            String csvContent = """
                    week,channel,visitors,ad_spend,discount_rate,page_load_ms,repeat_customer_share,orders,revenue,conversion_rate
                    1,email,100,25,0.05,340,0.40,12,1200,0.120
                    2,email,110,30,0.04,330,0.41,14,1450,0.127
                    """
                    .replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\r", "\\r")
                    .replace("\n", "\\n");
            HttpRequest upload = HttpRequest.newBuilder(root.resolve("/api/upload"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(
                            "{\"filename\":\"phase-g.csv\",\"content\":\"" + csvContent + "\"}"))
                    .build();
            HttpResponse<String> uploaded = client.send(upload, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, uploaded.statusCode());
            assertTrue(uploaded.body().contains("\"status\" : \"uploaded\""));
            assertTrue(uploaded.body().contains("\"rows\" : 2"));
            assertTrue(uploaded.body().contains("email"));

            HttpResponse<String> uploadedSession = client.send(
                    HttpRequest.newBuilder(root.resolve("/api/session")).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(200, uploadedSession.statusCode());
            assertTrue(uploadedSession.body().contains("\"rows\" : 2"));
            assertTrue(uploadedSession.body().contains("phase-g.csv"));
        }
    }

    @Test
    void servesDashboardUiAndFilterEndpoint() throws Exception {
        Path csv = AnalyticsInsightAgent.sampleCsv();
        Path database = tempDir.resolve("analytics.sqlite");
        Path spec = tempDir.resolve("dashboard.json");
        Path html = tempDir.resolve("dashboard.html");
        AnalyticsFrame frame = AnalyticsFrame.loadCsv(csv);

        try (AnalyticsDatabase analytics = AnalyticsDatabase.importCsv(database, csv)) {
            AnalyticsDashboard.create(analytics, frame, spec, html);

            try (AnalyticsDashboardServer server = AnalyticsDashboardServer.start(analytics, spec, html)) {
                HttpClient client = HttpClient.newHttpClient();
                URI root = server.uri();

                HttpResponse<String> ui = client.send(
                        HttpRequest.newBuilder(root).GET().build(),
                        HttpResponse.BodyHandlers.ofString());
                assertEquals(200, ui.statusCode());
                assertTrue(
                        ui.body().contains("Analytics Workbench")
                                || ui.body().contains("Analytics Insight Dashboard"));
                assertTrue(ui.body().contains("id=\"root\"") || ui.body().contains("Upload CSV"));

                HttpResponse<String> dashboard = client.send(
                        HttpRequest.newBuilder(root.resolve("/api/dashboard")).GET().build(),
                        HttpResponse.BodyHandlers.ofString());
                assertEquals(200, dashboard.statusCode());
                assertTrue(dashboard.body().contains("\"filters\""));
                assertTrue(dashboard.body().contains("chart-revenue-channel"));

                HttpResponse<String> filterOptions = client.send(
                        HttpRequest.newBuilder(root.resolve("/api/filter-options")).GET().build(),
                        HttpResponse.BodyHandlers.ofString());
                assertEquals(200, filterOptions.statusCode());
                assertTrue(filterOptions.body().contains("\"values\""));
                assertTrue(filterOptions.body().contains("paid_search"));
                assertTrue(filterOptions.body().contains("direct"));

                HttpResponse<String> connectors = client.send(
                        HttpRequest.newBuilder(root.resolve("/api/connectors")).GET().build(),
                        HttpResponse.BodyHandlers.ofString());
                assertEquals(200, connectors.statusCode());
                assertTrue(connectors.body().contains("\"connectors\""));
                assertTrue(connectors.body().contains("Hugging Face Datasets"));
                assertTrue(connectors.body().contains("BigQuery Public"));
                assertTrue(connectors.body().contains("\"rawDataSentToModel\" : false"));

                HttpRequest connect = HttpRequest.newBuilder(root.resolve("/api/connect"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString("""
                                {
                                  "connectorId": "bigquery-public",
                                  "datasetId": "bq-ga-sessions",
                                  "sampleRows": 5000
                                }
                                """))
                        .build();
                HttpResponse<String> connectPlan = client.send(connect, HttpResponse.BodyHandlers.ofString());
                assertEquals(200, connectPlan.statusCode());
                assertTrue(connectPlan.body().contains("\"status\" : \"planned\""));
                assertTrue(connectPlan.body().contains("\"connectorId\" : \"bigquery-public\""));
                assertTrue(connectPlan.body().contains("\"sampleRows\" : 1000"));
                assertTrue(connectPlan.body().contains("\"rawDataSentToModel\" : false"));
                assertTrue(connectPlan.body().contains("Metadata-only plan"));

                HttpResponse<String> schema = client.send(
                        HttpRequest.newBuilder(root.resolve("/api/schema")).GET().build(),
                        HttpResponse.BodyHandlers.ofString());
                assertEquals(200, schema.statusCode());
                assertTrue(schema.body().contains("\"columns\""));
                assertTrue(schema.body().contains("\"revenue\""));

                HttpResponse<String> profile = client.send(
                        HttpRequest.newBuilder(root.resolve("/api/profile")).GET().build(),
                        HttpResponse.BodyHandlers.ofString());
                assertEquals(200, profile.statusCode());
                assertTrue(profile.body().contains("\"columns\""));
                assertTrue(profile.body().contains("\"missing\""));

                HttpResponse<String> quality = client.send(
                        HttpRequest.newBuilder(root.resolve("/api/quality")).GET().build(),
                        HttpResponse.BodyHandlers.ofString());
                assertEquals(200, quality.statusCode());
                assertTrue(quality.body().contains("\"duplicateRowGroups\""));

                HttpResponse<String> sample = client.send(
                        HttpRequest.newBuilder(root.resolve("/api/sample")).GET().build(),
                        HttpResponse.BodyHandlers.ofString());
                assertEquals(200, sample.statusCode());
                assertTrue(sample.body().contains("\"rows\""));
                assertTrue(sample.body().contains("paid_search"));

                HttpRequest filter = HttpRequest.newBuilder(root.resolve("/api/filter"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString("""
                                {
                                  "filters": {
                                    "channel": ["paid_search"],
                                    "week": {"from": 1, "to": 4}
                                  },
                                  "drilldownTileId": "chart-revenue-channel",
                                  "drilldownValue": "paid_search"
                                }
                                """))
                        .build();
                HttpResponse<String> filtered = client.send(filter, HttpResponse.BodyHandlers.ofString());

                assertEquals(200, filtered.statusCode());
                assertTrue(filtered.body().contains("\"kpi-rows\""));
                assertTrue(filtered.body().contains("\"total_revenue\""));
                assertTrue(filtered.body().contains("\"drilldown\""));
                assertTrue(filtered.body().contains("paid_search"));
                assertFalse(filtered.body().contains("social"));

                String csvContent = """
                        week,channel,visitors,ad_spend,discount_rate,page_load_ms,repeat_customer_share,orders,revenue,conversion_rate
                        1,partner,100,50,0.01,500,0.60,10,1000,0.100
                        2,retail,140,80,0.02,520,0.50,18,2200,0.129
                        3,retail,160,90,0.03,540,0.52,20,2600,0.125
                        """
                        .replace("\\", "\\\\")
                        .replace("\"", "\\\"")
                        .replace("\r", "\\r")
                        .replace("\n", "\\n");
                HttpRequest upload = HttpRequest.newBuilder(root.resolve("/api/upload"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(
                                "{\"filename\":\"uploaded.csv\",\"content\":\"" + csvContent + "\"}"))
                        .build();
                HttpResponse<String> uploaded = client.send(upload, HttpResponse.BodyHandlers.ofString());
                assertEquals(200, uploaded.statusCode());
                assertTrue(uploaded.body().contains("\"status\" : \"uploaded\""));
                assertTrue(uploaded.body().contains("\"rows\" : 3"));
                assertTrue(uploaded.body().contains("\"filterOptions\""));
                assertTrue(uploaded.body().contains("partner"));
                assertTrue(uploaded.body().contains("retail"));
                assertFalse(uploaded.body().contains("paid_search"));

                HttpRequest chat = HttpRequest.newBuilder(root.resolve("/api/chat"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(
                                "{\"question\":\"What features are important for revenue?\"}"))
                        .build();
                HttpResponse<String> chatResponse = client.send(chat, HttpResponse.BodyHandlers.ofString());
                assertEquals(200, chatResponse.statusCode());
                assertTrue(chatResponse.body().contains("\"tool\" : \"interesting_features\""));
                assertTrue(chatResponse.body().contains("\"model\""));
                assertTrue(chatResponse.body().contains("\"result\""));
                assertTrue(chatResponse.body().contains("\"features\""));
                assertTrue(chatResponse.body().contains("\"answer\""));

                HttpRequest causalChat = HttpRequest.newBuilder(root.resolve("/api/chat"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(
                                "{\"question\":\"Does discount_rate cause revenue?\"}"))
                        .build();
                HttpResponse<String> causalResponse = client.send(causalChat, HttpResponse.BodyHandlers.ofString());
                assertEquals(200, causalResponse.statusCode());
                assertTrue(causalResponse.body().contains("\"tool\" : \"causal_sanity_check\""));
                assertTrue(causalResponse.body().contains("\"canClaimCausation\" : false"));

                String[] analyses = {
                    "profile",
                    "correlations",
                    "features",
                    "segments",
                    "regression",
                    "anomalies",
                    "forecast",
                    "causal-check"
                };
                for (String analysis : analyses) {
                    HttpRequest analysisRequest = HttpRequest.newBuilder(root.resolve("/api/analysis/" + analysis))
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString("{}"))
                            .build();
                    HttpResponse<String> analysisResponse =
                            client.send(analysisRequest, HttpResponse.BodyHandlers.ofString());
                    assertEquals(200, analysisResponse.statusCode(), analysis);
                    assertTrue(analysisResponse.body().contains("\"analysis\" : \"" + analysis + "\""), analysis);
                    assertTrue(analysisResponse.body().contains("\"result\""), analysis);
                    assertTrue(analysisResponse.body().contains("\"tileCandidate\""), analysis);
                }

                HttpRequest pin = HttpRequest.newBuilder(root.resolve("/api/dashboard/pin"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString("""
                                {
                                  "id": "pinned-test",
                                  "title": "Pinned Revenue Insight",
                                  "body": "Revenue analysis pinned from the dashboard builder.",
                                  "source": "features",
                                  "evidenceIds": ["features"]
                                }
                                """))
                        .build();
                HttpResponse<String> pinned = client.send(pin, HttpResponse.BodyHandlers.ofString());
                assertEquals(200, pinned.statusCode());
                assertTrue(pinned.body().contains("\"status\" : \"pinned\""));
                assertTrue(pinned.body().contains("\"tileId\" : \"pinned-test\""));
                assertTrue(pinned.body().contains("Pinned Revenue Insight"));

                HttpRequest save = HttpRequest.newBuilder(root.resolve("/api/dashboard/save"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString("{}"))
                        .build();
                HttpResponse<String> saved = client.send(save, HttpResponse.BodyHandlers.ofString());
                assertEquals(200, saved.statusCode());
                assertTrue(saved.body().contains("\"status\" : \"saved\""));
                assertTrue(saved.body().contains("\"dashboardSpec\""));

                HttpRequest reload = HttpRequest.newBuilder(root.resolve("/api/dashboard/reload"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString("{}"))
                        .build();
                HttpResponse<String> reloaded = client.send(reload, HttpResponse.BodyHandlers.ofString());
                assertEquals(200, reloaded.statusCode());
                assertTrue(reloaded.body().contains("\"status\" : \"reloaded\""));
                assertTrue(reloaded.body().contains("Pinned Revenue Insight"));

                HttpResponse<String> jsonExport = client.send(
                        HttpRequest.newBuilder(root.resolve("/api/dashboard/export/json")).GET().build(),
                        HttpResponse.BodyHandlers.ofString());
                assertEquals(200, jsonExport.statusCode());
                assertTrue(jsonExport.body().contains("Pinned Revenue Insight"));

                HttpResponse<String> htmlExport = client.send(
                        HttpRequest.newBuilder(root.resolve("/api/dashboard/export/html")).GET().build(),
                        HttpResponse.BodyHandlers.ofString());
                assertEquals(200, htmlExport.statusCode());
                assertTrue(htmlExport.body().contains("Analytics Insight Dashboard"));
                assertTrue(htmlExport.body().contains("Pinned Revenue Insight"));
            }
        }
    }
}
