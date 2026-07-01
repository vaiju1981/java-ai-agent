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
                assertTrue(ui.body().contains("Analytics Dashboard"));
                assertTrue(ui.body().contains("/api/filter"));

                HttpResponse<String> dashboard = client.send(
                        HttpRequest.newBuilder(root.resolve("/api/dashboard")).GET().build(),
                        HttpResponse.BodyHandlers.ofString());
                assertEquals(200, dashboard.statusCode());
                assertTrue(dashboard.body().contains("\"filters\""));
                assertTrue(dashboard.body().contains("chart-revenue-channel"));

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
            }
        }
    }
}
