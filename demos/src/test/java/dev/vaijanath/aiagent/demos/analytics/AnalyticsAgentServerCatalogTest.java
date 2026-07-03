package dev.vaijanath.aiagent.demos.analytics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AnalyticsAgentServerCatalogTest {

    @TempDir
    Path tempDir;

    @Test
    void editingADescriptionThroughTheApiTakesEffectImmediately() throws Exception {
        Map<String, Path> csvs = new LinkedHashMap<>();
        csvs.put("customers", Fixtures.customers(tempDir));
        csvs.put("orders", Fixtures.orders(tempDir));
        try (CsvSource src = CsvSource.of(tempDir.resolve("srvcat.duckdb"), csvs);
                AnalyticsAgentServer server = AnalyticsAgentServer.start(
                        src, null, Catalog.load(tempDir.resolve("catalog.json")))) {

            HttpResponse<String> before = get(server.uri().resolve("api/catalog"));
            assertEquals(200, before.statusCode());

            HttpResponse<String> post = post(server.uri().resolve("api/catalog/descriptions/column"),
                    "{\"table\":\"orders\",\"column\":\"revenue\",\"description\":\"booking amount\"}");
            assertEquals(200, post.statusCode());

            String catalog = get(server.uri().resolve("api/catalog")).body();
            assertTrue(catalog.contains("booking amount"), "edited description must be visible: " + catalog);
        }
    }

    @Test
    void includingARelationshipThroughTheApiSurvivesRebuildAndAppearsInTheCatalog() throws Exception {
        Map<String, Path> csvs = new LinkedHashMap<>();
        csvs.put("customers", Fixtures.customers(tempDir));
        csvs.put("orders", Fixtures.orders(tempDir));
        try (CsvSource src = CsvSource.of(tempDir.resolve("srvrel.duckdb"), csvs);
                AnalyticsAgentServer server = AnalyticsAgentServer.start(
                        src, null, Catalog.load(tempDir.resolve("catalog.json")))) {

            HttpResponse<String> post = post(server.uri().resolve("api/catalog/relationships/include"), """
                    {"fromTable":"orders","fromColumns":["customer_id"],"toTable":"customers",
                     "toColumns":["customer_id"],"cardinality":"many_to_one"}
                    """);
            assertEquals(200, post.statusCode());

            String catalog = get(server.uri().resolve("api/catalog")).body();
            assertTrue(catalog.contains("\"fromTable\":\"orders\""), "included relationship must be visible: " + catalog);
            assertTrue(catalog.contains("\"toTable\":\"customers\""), "included relationship must be visible: " + catalog);
        }
    }

    private static HttpResponse<String> get(URI uri) throws Exception {
        return HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(uri).GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    private static HttpResponse<String> post(URI uri, String body) throws Exception {
        return HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(uri).header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body)).build(),
                HttpResponse.BodyHandlers.ofString());
    }
}
