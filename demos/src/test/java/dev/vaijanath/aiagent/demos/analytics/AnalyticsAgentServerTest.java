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

class AnalyticsAgentServerTest {

    @TempDir
    Path tempDir;

    @Test
    void servesProfileAndModelWithoutAnLlm() throws Exception {
        Map<String, Path> csvs = new LinkedHashMap<>();
        csvs.put("customers", Fixtures.customers(tempDir));
        csvs.put("orders", Fixtures.orders(tempDir));
        try (CsvSource src = CsvSource.of(tempDir.resolve("srv.duckdb"), csvs);
                AnalyticsAgentServer server = AnalyticsAgentServer.start(src, null)) {

            String profile = get(server.uri().resolve("api/profile"));
            assertTrue(profile.contains("IDENTIFIER"), "profile served with roles");

            String model = get(server.uri().resolve("api/model"));
            assertTrue(model.contains("orders.revenue"), "model served with metric refs");

            HttpResponse<String> ask = post(server.uri().resolve("api/ask"), "{\"question\":\"hi\"}");
            assertEquals(400, ask.statusCode(), "ask disabled with no model configured");
            assertTrue(ask.body().contains("no model configured"));
        }
    }

    private static String get(URI uri) throws Exception {
        HttpResponse<String> r = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(uri).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, r.statusCode());
        return r.body();
    }

    private static HttpResponse<String> post(URI uri, String body) throws Exception {
        return HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(uri).header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body)).build(),
                HttpResponse.BodyHandlers.ofString());
    }
}
