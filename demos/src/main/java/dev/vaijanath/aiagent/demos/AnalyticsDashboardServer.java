package dev.vaijanath.aiagent.demos;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class AnalyticsDashboardServer implements AutoCloseable {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    private final HttpServer server;
    private final ExecutorService executor;

    private AnalyticsDashboardServer(HttpServer server, ExecutorService executor) {
        this.server = server;
        this.executor = executor;
    }

    static AnalyticsDashboardServer start(AnalyticsDatabase analytics, Path dashboardSpec, Path dashboardHtml) {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            ExecutorService executor = Executors.newSingleThreadExecutor();
            AnalyticsDashboardServer hosted = new AnalyticsDashboardServer(server, executor);
            server.createContext("/", exchange -> hosted.route(exchange, analytics, dashboardSpec, dashboardHtml));
            server.setExecutor(executor);
            server.start();
            return hosted;
        } catch (IOException e) {
            throw new IllegalStateException("failed to start analytics dashboard server", e);
        }
    }

    URI uri() {
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/");
    }

    @Override
    public void close() {
        server.stop(0);
        executor.shutdownNow();
    }

    private void route(HttpExchange exchange, AnalyticsDatabase analytics, Path dashboardSpec, Path dashboardHtml)
            throws IOException {
        try {
            String path = exchange.getRequestURI().getPath();
            String method = exchange.getRequestMethod();
            if ("GET".equals(method) && "/".equals(path)) {
                respondHtml(exchange, hostedHtml());
                return;
            }
            if ("GET".equals(method) && "/api/dashboard".equals(path)) {
                respondJson(exchange, Files.readString(dashboardSpec, StandardCharsets.UTF_8));
                return;
            }
            if ("POST".equals(method) && "/api/filter".equals(path)) {
                FilterRequest request = filterRequest(exchange);
                AnalyticsDashboard.DashboardFilterResult result = AnalyticsDashboard.applyFilters(
                        analytics,
                        dashboardSpec,
                        dashboardHtml,
                        request.filters(),
                        request.drilldownTileId(),
                        request.drilldownValue());
                respondJson(exchange, toJson(Map.of(
                        "dashboard", result.html().toString(),
                        "dashboardSpec", result.spec().toString(),
                        "result", result.result())));
                return;
            }
            respond(exchange, 404, "text/plain; charset=utf-8", "not found");
        } catch (RuntimeException e) {
            respond(exchange, 500, "application/json; charset=utf-8", toJson(Map.of("error", e.getMessage())));
        }
    }

    private static FilterRequest filterRequest(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Map<String, Object> parsed = MAPPER.readValue(body, new TypeReference<>() {});
        Object filters = parsed.get("filters");
        if (!(filters instanceof Map<?, ?>)) {
            throw new IllegalArgumentException("filters object is required");
        }
        Map<String, Object> typedFilters = new LinkedHashMap<>();
        ((Map<?, ?>) filters).forEach((key, value) -> typedFilters.put(String.valueOf(key), value));
        return new FilterRequest(
                typedFilters,
                string(parsed.get("drilldownTileId")),
                string(parsed.get("drilldownValue")));
    }

    private static String hostedHtml() {
        return """
                <!doctype html>
                <html lang="en">
                <head>
                  <meta charset="utf-8">
                  <title>Analytics Dashboard</title>
                  <style>
                    body { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif; margin: 24px; color: #1f2937; }
                    header { display: flex; align-items: center; justify-content: space-between; gap: 16px; }
                    button { border: 1px solid #9ca3af; background: #ffffff; border-radius: 6px; padding: 8px 12px; cursor: pointer; }
                    main { display: grid; grid-template-columns: 280px minmax(0, 1fr); gap: 16px; margin-top: 20px; }
                    section { border: 1px solid #d1d5db; border-radius: 8px; padding: 14px; background: #ffffff; }
                    label { display: block; font-size: 13px; margin: 12px 0 4px; color: #374151; }
                    input { box-sizing: border-box; width: 100%; padding: 8px; border: 1px solid #cbd5e1; border-radius: 6px; }
                    pre { white-space: pre-wrap; overflow: auto; background: #f9fafb; padding: 12px; border-radius: 6px; max-height: 70vh; }
                  </style>
                </head>
                <body>
                  <header>
                    <h1>Analytics Dashboard</h1>
                    <button id="refresh">Refresh</button>
                  </header>
                  <main>
                    <section>
                      <h2>Filters</h2>
                      <label for="channel">Channel</label>
                      <input id="channel" value="paid_search">
                      <label for="from">Week From</label>
                      <input id="from" type="number" value="1">
                      <label for="to">Week To</label>
                      <input id="to" type="number" value="4">
                      <button id="apply">Apply</button>
                    </section>
                    <section>
                      <h2>Dashboard Model</h2>
                      <pre id="output"></pre>
                    </section>
                  </main>
                  <script>
                    const output = document.getElementById('output');
                    async function loadDashboard() {
                      const response = await fetch('/api/dashboard');
                      output.textContent = JSON.stringify(await response.json(), null, 2);
                    }
                    async function applyFilters() {
                      const channel = document.getElementById('channel').value;
                      const from = Number(document.getElementById('from').value);
                      const to = Number(document.getElementById('to').value);
                      const response = await fetch('/api/filter', {
                        method: 'POST',
                        headers: {'Content-Type': 'application/json'},
                        body: JSON.stringify({
                          filters: {channel: [channel], week: {from, to}},
                          drilldownTileId: 'chart-revenue-channel',
                          drilldownValue: channel
                        })
                      });
                      output.textContent = JSON.stringify(await response.json(), null, 2);
                    }
                    document.getElementById('refresh').onclick = loadDashboard;
                    document.getElementById('apply').onclick = applyFilters;
                    loadDashboard();
                  </script>
                </body>
                </html>
                """;
    }

    private static void respondHtml(HttpExchange exchange, String body) throws IOException {
        respond(exchange, 200, "text/html; charset=utf-8", body);
    }

    private static void respondJson(HttpExchange exchange, String json) throws IOException {
        respond(exchange, 200, "application/json; charset=utf-8", json);
    }

    private static void respond(HttpExchange exchange, int status, String contentType, String body)
            throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().put("Content-Type", List.of(contentType));
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream stream = exchange.getResponseBody()) {
            stream.write(bytes);
        }
    }

    private static String string(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static String toJson(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("failed to serialize dashboard server response", e);
        }
    }

    private record FilterRequest(Map<String, Object> filters, String drilldownTileId, String drilldownValue) {}
}
