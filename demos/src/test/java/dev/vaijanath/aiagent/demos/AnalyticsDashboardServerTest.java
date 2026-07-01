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
                assertTrue(ui.body().contains("Analytics Insight Dashboard"));
                assertTrue(ui.body().contains("Revenue By Channel"));
                assertTrue(ui.body().contains("Total Revenue"));
                assertTrue(ui.body().contains("Channel Rollup"));
                assertTrue(ui.body().contains("id=\"channels\""));
                assertTrue(ui.body().contains("Upload CSV"));
                assertTrue(ui.body().contains("Ask The Data"));
                assertTrue(ui.body().contains("/api/filter"));
                assertTrue(ui.body().contains("/api/upload"));
                assertTrue(ui.body().contains("/api/chat"));

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
            }
        }
    }
}
