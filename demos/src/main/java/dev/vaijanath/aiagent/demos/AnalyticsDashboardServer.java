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
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class AnalyticsDashboardServer implements AutoCloseable {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);
    private static final String OLLAMA_MODEL = "gemma4:31b-cloud";

    private final HttpServer server;
    private final ExecutorService executor;
    private final DashboardWorkspace workspace;

    private AnalyticsDashboardServer(HttpServer server, ExecutorService executor, DashboardWorkspace workspace) {
        this.server = server;
        this.executor = executor;
        this.workspace = workspace;
    }

    public static void main(String[] args) throws Exception {
        Path csv = args.length > 0 ? Path.of(args[0]) : AnalyticsInsightAgent.sampleCsv();
        Path database = Files.createTempFile("java-ai-agent-analytics-ui-", ".sqlite");
        Path dashboardSpec = Files.createTempFile("java-ai-agent-dashboard-ui-", ".json");
        Path dashboardHtml = Files.createTempFile("java-ai-agent-dashboard-ui-", ".html");
        AnalyticsFrame frame = AnalyticsFrame.loadCsv(csv);
        AnalyticsDatabase analytics = AnalyticsDatabase.importCsv(database, csv);
        AnalyticsDashboard.create(analytics, frame, dashboardSpec, dashboardHtml);
        AnalyticsDashboardServer hosted = startOwned(analytics, csv, database, dashboardSpec, dashboardHtml);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            hosted.close();
        }));
        System.out.println("Analytics dashboard UI: " + hosted.uri());
        new CountDownLatch(1).await();
    }

    static AnalyticsDashboardServer start(AnalyticsDatabase analytics, Path dashboardSpec, Path dashboardHtml) {
        return start(analytics, Path.of("sample.csv"), Path.of(""), dashboardSpec, dashboardHtml, false);
    }

    private static AnalyticsDashboardServer startOwned(
            AnalyticsDatabase analytics,
            Path csv,
            Path database,
            Path dashboardSpec,
            Path dashboardHtml) {
        return start(analytics, csv, database, dashboardSpec, dashboardHtml, true);
    }

    private static AnalyticsDashboardServer start(
            AnalyticsDatabase analytics,
            Path csv,
            Path database,
            Path dashboardSpec,
            Path dashboardHtml,
            boolean ownsAnalytics) {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", configuredPort()), 0);
            ExecutorService executor = Executors.newSingleThreadExecutor();
            DashboardWorkspace workspace =
                    new DashboardWorkspace(analytics, csv, database, dashboardSpec, dashboardHtml, ownsAnalytics);
            AnalyticsDashboardServer hosted = new AnalyticsDashboardServer(server, executor, workspace);
            server.createContext("/", hosted::route);
            server.setExecutor(executor);
            server.start();
            return hosted;
        } catch (IOException e) {
            throw new IllegalStateException("failed to start analytics dashboard server", e);
        }
    }

    private static int configuredPort() {
        String value = System.getenv("ANALYTICS_PORT");
        if (value == null || value.isBlank()) {
            return 0;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("ANALYTICS_PORT must be a number", e);
        }
    }

    URI uri() {
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/");
    }

    @Override
    public void close() {
        server.stop(0);
        executor.shutdownNow();
        workspace.close();
    }

    private void route(HttpExchange exchange) throws IOException {
        try {
            String path = exchange.getRequestURI().getPath();
            String method = exchange.getRequestMethod();
            if ("GET".equals(method) && "/".equals(path)) {
                respondHtml(exchange, hostedHtml());
                return;
            }
            if ("GET".equals(method) && "/api/dashboard".equals(path)) {
                respondJson(exchange, workspace.dashboardJson());
                return;
            }
            if ("GET".equals(method) && "/api/session".equals(path)) {
                respondJson(exchange, workspace.sessionJson());
                return;
            }
            if ("GET".equals(method) && "/api/filter-options".equals(path)) {
                respondJson(exchange, workspace.filterOptionsJson());
                return;
            }
            if ("GET".equals(method) && "/api/schema".equals(path)) {
                respondJson(exchange, workspace.schemaJson());
                return;
            }
            if ("GET".equals(method) && "/api/profile".equals(path)) {
                respondJson(exchange, workspace.profileJson());
                return;
            }
            if ("GET".equals(method) && "/api/quality".equals(path)) {
                respondJson(exchange, workspace.qualityJson());
                return;
            }
            if ("GET".equals(method) && "/api/sample".equals(path)) {
                respondJson(exchange, workspace.sampleJson());
                return;
            }
            if ("POST".equals(method) && "/api/filter".equals(path)) {
                FilterRequest request = filterRequest(exchange);
                AnalyticsDashboard.DashboardFilterResult result =
                        workspace.applyFilters(request.filters(), request.drilldownTileId(), request.drilldownValue());
                respondJson(exchange, toJson(Map.of(
                        "dashboard", result.html().toString(),
                        "dashboardSpec", result.spec().toString(),
                        "result", result.result())));
                return;
            }
            if ("POST".equals(method) && "/api/upload".equals(path)) {
                respondJson(exchange, workspace.upload(uploadRequest(exchange)));
                return;
            }
            if ("POST".equals(method) && "/api/chat".equals(path)) {
                respondJson(exchange, workspace.chat(chatRequest(exchange)));
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

    private static UploadRequest uploadRequest(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Map<String, Object> parsed = MAPPER.readValue(body, new TypeReference<>() {});
        String filename = string(parsed.get("filename"));
        String content = string(parsed.get("content"));
        if (filename.isBlank()) {
            filename = "uploaded.csv";
        }
        if (content.isBlank()) {
            throw new IllegalArgumentException("CSV content is required");
        }
        return new UploadRequest(filename, content);
    }

    private static ChatRequest chatRequest(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Map<String, Object> parsed = MAPPER.readValue(body, new TypeReference<>() {});
        String question = string(parsed.get("question"));
        if (question.isBlank()) {
            throw new IllegalArgumentException("question is required");
        }
        return new ChatRequest(question);
    }

    private static String hostedHtml() {
        return """
                <!doctype html>
                <html lang="en">
                <head>
                  <meta charset="utf-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1">
                  <title>Analytics Dashboard</title>
                  <style>
                    :root {
                      color-scheme: light;
                      --bg: #f6f7f4;
                      --panel: #ffffff;
                      --ink: #202124;
                      --muted: #626b73;
                      --line: #d8ddd6;
                      --teal: #0f766e;
                      --blue: #2563eb;
                      --amber: #b45309;
                      --green: #15803d;
                      --rose: #be123c;
                    }
                    * { box-sizing: border-box; }
                    body {
                      margin: 0;
                      background: var(--bg);
                      color: var(--ink);
                      font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
                    }
                    header {
                      display: flex;
                      align-items: center;
                      justify-content: space-between;
                      gap: 16px;
                      padding: 16px 24px;
                      border-bottom: 1px solid var(--line);
                      background: #fbfcfa;
                    }
                    h1, h2, h3, p { margin: 0; }
                    h1 { font-size: 20px; font-weight: 700; }
                    h2 { font-size: 15px; font-weight: 700; }
                    h3 { font-size: 13px; font-weight: 700; color: var(--muted); }
                    button {
                      border: 1px solid var(--line);
                      background: #ffffff;
                      border-radius: 6px;
                      padding: 8px 11px;
                      color: var(--ink);
                      cursor: pointer;
                      font-weight: 650;
                    }
                    button.primary { background: var(--teal); border-color: var(--teal); color: #ffffff; }
                    button[aria-selected="true"] { border-color: var(--teal); color: var(--teal); }
                    input {
                      width: 100%;
                      border: 1px solid var(--line);
                      border-radius: 6px;
                      padding: 8px 10px;
                      background: #ffffff;
                    }
                    textarea {
                      width: 100%;
                      min-height: 74px;
                      resize: vertical;
                      border: 1px solid var(--line);
                      border-radius: 6px;
                      padding: 9px 10px;
                      font: inherit;
                    }
                    label { display: block; color: var(--muted); font-size: 12px; font-weight: 700; margin-bottom: 6px; }
                    .shell {
                      display: grid;
                      grid-template-columns: 280px minmax(0, 1fr);
                      min-height: calc(100vh - 65px);
                    }
                    aside {
                      border-right: 1px solid var(--line);
                      padding: 18px;
                      background: #fbfcfa;
                    }
                    .workspace { padding: 18px 24px 28px; }
                    .stack { display: grid; gap: 14px; }
                    .row { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
                    .spacer { flex: 1; }
                    .status { font-size: 12px; color: var(--muted); }
                    .filter-group { display: grid; gap: 8px; }
                    .check {
                      display: flex;
                      align-items: center;
                      gap: 8px;
                      border: 1px solid var(--line);
                      border-radius: 6px;
                      padding: 8px 9px;
                      background: #ffffff;
                    }
                    .check input { width: auto; }
                    .range { display: grid; grid-template-columns: 1fr 1fr; gap: 8px; }
                    .metric-grid {
                      display: grid;
                      grid-template-columns: repeat(3, minmax(0, 1fr));
                      gap: 12px;
                    }
                    .tile {
                      background: var(--panel);
                      border: 1px solid var(--line);
                      border-radius: 8px;
                      padding: 14px;
                    }
                    .metric-value { font-size: 25px; font-weight: 760; margin-top: 8px; }
                    .metric-subtitle { color: var(--muted); font-size: 12px; margin-top: 5px; }
                    .content-grid {
                      display: grid;
                      grid-template-columns: minmax(0, 1.5fr) minmax(320px, 0.9fr);
                      gap: 12px;
                      margin-top: 12px;
                    }
                    .chart { display: grid; gap: 9px; margin-top: 14px; }
                    .bar-row { display: grid; grid-template-columns: 116px minmax(0, 1fr) 88px; gap: 10px; align-items: center; }
                    .bar-label { font-size: 12px; color: var(--muted); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
                    .bar-track { height: 18px; background: #edf0ea; border-radius: 5px; overflow: hidden; }
                    .bar-fill { height: 100%; background: var(--blue); }
                    .bar-fill.direct { background: var(--green); }
                    .bar-fill.email { background: var(--amber); }
                    .bar-fill.social { background: var(--rose); }
                    .bar-value { font-size: 12px; text-align: right; font-variant-numeric: tabular-nums; }
                    table { width: 100%; border-collapse: collapse; margin-top: 12px; }
                    th, td { border-bottom: 1px solid var(--line); padding: 9px 8px; text-align: left; font-size: 13px; }
                    th { color: var(--muted); font-size: 12px; }
                    .tabs { display: flex; gap: 8px; margin-bottom: 12px; }
                    .panel[hidden] { display: none; }
                    pre {
                      max-height: 58vh;
                      overflow: auto;
                      white-space: pre-wrap;
                      background: #f1f3ef;
                      border: 1px solid var(--line);
                      border-radius: 8px;
                      padding: 12px;
                      font-size: 12px;
                    }
                    .empty { color: var(--muted); padding: 18px 0; }
                    .chat-answer {
                      margin-top: 12px;
                      display: grid;
                      gap: 10px;
                    }
                    .chat-answer .answer {
                      border-left: 3px solid var(--teal);
                      padding-left: 10px;
                      line-height: 1.5;
                    }
                    .file-name { font-size: 12px; color: var(--muted); line-height: 1.4; }
                    @media (max-width: 860px) {
                      .shell { grid-template-columns: 1fr; }
                      aside { border-right: 0; border-bottom: 1px solid var(--line); }
                      .metric-grid, .content-grid { grid-template-columns: 1fr; }
                      .bar-row { grid-template-columns: 92px minmax(0, 1fr) 76px; }
                    }
                  </style>
                </head>
                <body>
                  <header>
                    <div>
                      <h1>Analytics Insight Dashboard</h1>
                      <div class="status" id="status">Starting</div>
                    </div>
                    <div class="row">
                      <button id="refresh">Refresh</button>
                      <button class="primary" id="applyTop">Apply Filters</button>
                    </div>
                  </header>
                  <div class="shell">
                    <aside class="stack">
                      <section class="stack">
                        <h2>Upload CSV</h2>
                        <input id="csvFile" type="file" accept=".csv,text/csv">
                        <button id="upload">Upload Dataset</button>
                        <div class="file-name" id="uploadStatus">Using sample dataset</div>
                      </section>
                      <section class="stack">
                        <h2>Filters</h2>
                        <div class="filter-group" id="channels"></div>
                        <div>
                          <label>Week Range</label>
                          <div class="range">
                            <input id="from" type="number" min="1" value="1">
                            <input id="to" type="number" min="1" value="16">
                          </div>
                        </div>
                        <button class="primary" id="apply">Apply Filters</button>
                      </section>
                      <section class="stack">
                        <h2>Selection</h2>
                        <div class="status" id="selection">All channels, weeks 1-16</div>
                      </section>
                    </aside>
                    <main class="workspace">
                      <div class="tabs">
                        <button id="tabOverview" aria-selected="true">Overview</button>
                        <button id="tabJson" aria-selected="false">JSON</button>
                      </div>
                      <section id="overview" class="panel">
                        <div class="metric-grid" id="metrics"></div>
                        <div class="content-grid">
                          <section class="tile">
                            <div class="row">
                              <h2>Revenue By Channel</h2>
                              <div class="spacer"></div>
                              <div class="status" id="chartStatus">Ready</div>
                            </div>
                            <div class="chart" id="chart"></div>
                          </section>
                          <section class="tile">
                            <h2>Decision Caveat</h2>
                            <div id="insight" class="empty"></div>
                          </section>
                          <section class="tile">
                            <h2>Channel Rollup</h2>
                            <div id="table"></div>
                          </section>
                          <section class="tile">
                            <h2>Drilldown</h2>
                            <div id="drilldown"></div>
                          </section>
                          <section class="tile">
                            <h2>Ask The Data</h2>
                            <textarea id="question" placeholder="Ask about correlations, features, causality, anomalies, forecasts, regression, or channel performance.">What features are important for revenue?</textarea>
                            <div class="row" style="margin-top:10px">
                              <button class="primary" id="ask">Ask</button>
                              <div class="status" id="chatStatus">Ready</div>
                            </div>
                            <div class="chat-answer" id="chatAnswer"></div>
                          </section>
                        </div>
                      </section>
                      <section id="jsonPanel" class="panel" hidden>
                        <pre id="output"></pre>
                      </section>
                    </main>
                  </div>
                  <script>
                    const state = { dashboard: null, filterResult: null, filterOptions: null };
                    const output = document.getElementById('output');
                    const status = document.getElementById('status');

                    function number(value) {
                      const n = Number(value);
                      return Number.isFinite(n) ? n : 0;
                    }

                    function fmt(value) {
                      const n = number(value);
                      return n.toLocaleString(undefined, { maximumFractionDigits: 2 });
                    }

                    function money(value) {
                      return '$' + fmt(value);
                    }

                    function percent(value) {
                      return (number(value) * 100).toLocaleString(undefined, { maximumFractionDigits: 1 }) + '%';
                    }

                    function rowValue(row, names) {
                      for (const name of names) {
                        if (row && row[name] !== undefined) return row[name];
                      }
                      const key = Object.keys(row || {}).find(k => names.some(name => k.toLowerCase().includes(name)));
                      return key ? row[key] : undefined;
                    }

                    function tile(id) {
                      return (state.dashboard?.tiles || []).find(item => item.id === id) || {};
                    }

                    function filteredTile(id) {
                      return (state.filterResult?.result?.tiles || []).find(item => item.tileId === id)?.data?.rows || null;
                    }

                    function chartRows() {
                      const filtered = filteredTile('chart-revenue-channel');
                      if (filtered) return filtered;
                      return tile('chart-revenue-channel')?.chart?.data?.values || [];
                    }

                    function tableRows() {
                      const filtered = filteredTile('table-channel');
                      if (filtered) return filtered;
                      return tile('table-channel')?.data?.rows || [];
                    }

                    function renderChannelControls() {
                      const channelFilter = (state.filterOptions?.filters || []).find(filter => filter.id === 'channel');
                      const channels = channelFilter?.values || [];
                      document.getElementById('channels').innerHTML = channels.map(channel => `
                        <label class="check">
                          <input type="checkbox" value="${channel}" checked>
                          <span>${channel}</span>
                        </label>
                      `).join('') || '<div class="empty">No channel values found</div>';
                      const weekFilter = (state.filterOptions?.filters || []).find(filter => filter.id === 'week');
                      const min = Math.floor(Number(weekFilter?.range?.min ?? 1));
                      const max = Math.ceil(Number(weekFilter?.range?.max ?? 16));
                      document.getElementById('from').value = String(min);
                      document.getElementById('to').value = String(max);
                      document.getElementById('from').min = String(min);
                      document.getElementById('from').max = String(max);
                      document.getElementById('to').min = String(min);
                      document.getElementById('to').max = String(max);
                    }

                    async function loadDashboard() {
                      const response = await fetch('/api/dashboard');
                      state.dashboard = await response.json();
                      const filterResponse = await fetch('/api/filter-options');
                      state.filterOptions = await filterResponse.json();
                      state.filterResult = null;
                      renderChannelControls();
                      render();
                    }

                    async function uploadDataset() {
                      const file = document.getElementById('csvFile').files[0];
                      if (!file) {
                        document.getElementById('uploadStatus').textContent = 'Choose a CSV file first';
                        return;
                      }
                      document.getElementById('uploadStatus').textContent = 'Uploading ' + file.name;
                      const content = await file.text();
                      const response = await fetch('/api/upload', {
                        method: 'POST',
                        headers: {'Content-Type': 'application/json'},
                        body: JSON.stringify({filename: file.name, content})
                      });
                      const payload = await response.json();
                      if (!response.ok || payload.error) {
                        document.getElementById('uploadStatus').textContent = payload.error || 'Upload failed';
                        return;
                      }
                      state.dashboard = payload.dashboard;
                      state.filterOptions = payload.filterOptions;
                      state.filterResult = null;
                      document.getElementById('uploadStatus').textContent = `${file.name} loaded, ${payload.rows} rows`;
                      renderChannelControls();
                      render();
                    }

                    function renderMetrics() {
                      const rowCount = filteredTile('kpi-rows')?.[0]?.rows ?? tile('kpi-rows').value;
                      const revenue = filteredTile('kpi-revenue')?.[0]?.total_revenue ?? tile('kpi-revenue').value;
                      const conversion = filteredTile('kpi-conversion')?.[0]?.avg_conversion_rate ?? tile('kpi-conversion').value;
                      document.getElementById('metrics').innerHTML = [
                        ['Rows', fmt(rowCount), 'Imported records'],
                        ['Total Revenue', money(revenue), 'Filtered revenue'],
                        ['Avg Conversion', percent(conversion), 'Filtered conversion']
                      ].map(metric => `
                        <section class="tile">
                          <h3>${metric[0]}</h3>
                          <div class="metric-value">${metric[1]}</div>
                          <div class="metric-subtitle">${metric[2]}</div>
                        </section>
                      `).join('');
                    }

                    function renderChart() {
                      const rows = chartRows();
                      const values = rows.map(row => ({
                        channel: String(row.channel ?? row.Channel ?? row.value ?? 'unknown'),
                        revenue: number(rowValue(row, ['avg_revenue', 'total_revenue', 'revenue', 'mean']))
                      })).filter(row => row.revenue > 0);
                      const max = Math.max(1, ...values.map(row => row.revenue));
                      document.getElementById('chartStatus').textContent = values.length + ' channels';
                      document.getElementById('chart').innerHTML = values.map(row => {
                        const cls = row.channel.replace(/[^a-z0-9]/gi, '').toLowerCase();
                        return `
                          <div class="bar-row">
                            <div class="bar-label">${row.channel}</div>
                            <div class="bar-track"><div class="bar-fill ${cls}" style="width:${Math.max(2, row.revenue / max * 100)}%"></div></div>
                            <div class="bar-value">${money(row.revenue)}</div>
                          </div>
                        `;
                      }).join('') || '<div class="empty">No chart rows</div>';
                    }

                    function renderTable() {
                      const rows = tableRows();
                      if (!rows.length) {
                        document.getElementById('table').innerHTML = '<div class="empty">No rows</div>';
                        return;
                      }
                      const normalized = rows.map(row => ({
                        channel: row.channel ?? row.Channel ?? 'unknown',
                        avg: rowValue(row, ['avg_revenue', 'mean', 'revenue']),
                        total: rowValue(row, ['total_revenue', 'sum']),
                        rows: row.rows ?? row.Count ?? ''
                      }));
                      document.getElementById('table').innerHTML = `
                        <table>
                          <thead><tr><th>Channel</th><th>Avg Revenue</th><th>Total Revenue</th><th>Rows</th></tr></thead>
                          <tbody>${normalized.map(row => `
                            <tr>
                              <td>${row.channel}</td>
                              <td>${row.avg === undefined ? '-' : money(row.avg)}</td>
                              <td>${row.total === undefined ? '-' : money(row.total)}</td>
                              <td>${row.rows}</td>
                            </tr>
                          `).join('')}</tbody>
                        </table>
                      `;
                    }

                    function renderInsight() {
                      const insight = (state.dashboard?.tiles || []).find(item => item.type === 'insight');
                      document.getElementById('insight').innerHTML = insight
                        ? `<h3>${insight.title}</h3><p style="margin-top:10px; line-height:1.5">${insight.body}</p>`
                        : '<div class="empty">No insight tile</div>';
                    }

                    function renderDrilldown() {
                      const rows = state.filterResult?.result?.drilldown?.data?.rows || [];
                      if (!rows.length) {
                        document.getElementById('drilldown').innerHTML = '<div class="empty">No drilldown selected</div>';
                        return;
                      }
                      document.getElementById('drilldown').innerHTML = `
                        <table>
                          <thead><tr><th>Week</th><th>Channel</th><th>Visitors</th><th>Orders</th><th>Revenue</th><th>Conversion</th></tr></thead>
                          <tbody>${rows.map(row => `
                            <tr>
                              <td>${row.week}</td>
                              <td>${row.channel}</td>
                              <td>${fmt(row.visitors)}</td>
                              <td>${fmt(row.orders)}</td>
                              <td>${money(row.revenue)}</td>
                              <td>${percent(row.conversion_rate)}</td>
                            </tr>
                          `).join('')}</tbody>
                        </table>
                      `;
                    }

                    async function askQuestion() {
                      const question = document.getElementById('question').value.trim();
                      if (!question) return;
                      document.getElementById('chatStatus').textContent = 'Thinking';
                      const response = await fetch('/api/chat', {
                        method: 'POST',
                        headers: {'Content-Type': 'application/json'},
                        body: JSON.stringify({question})
                      });
                      const payload = await response.json();
                      document.getElementById('chatStatus').textContent = payload.error
                        ? 'Needs schema support'
                        : `${payload.tool} · ${payload.model}`;
                      document.getElementById('chatAnswer').innerHTML = `
                        <div class="answer">${payload.answer || payload.error}</div>
                        <pre>${JSON.stringify(payload.result || payload, null, 2)}</pre>
                      `;
                    }

                    function renderSelection() {
                      const filters = state.dashboard?.lastFilterResult?.filters || state.filterResult?.result?.filters;
                      if (!filters) {
                        document.getElementById('selection').textContent = 'All channels, weeks 1-16';
                        return;
                      }
                      const list = Array.isArray(filters.channel) ? filters.channel.join(', ') : 'all channels';
                      const week = filters.week || {};
                      document.getElementById('selection').textContent = `${list}, weeks ${week.from ?? 1}-${week.to ?? 16}`;
                    }

                    function render() {
                      if (!state.dashboard) return;
                      status.textContent = `Revision ${state.dashboard.revision || 1}`;
                      output.textContent = JSON.stringify({dashboard: state.dashboard, filter: state.filterResult}, null, 2);
                      renderMetrics();
                      renderChart();
                      renderTable();
                      renderInsight();
                      renderDrilldown();
                      renderSelection();
                    }

                    function switchTab(name) {
                      const overview = name === 'overview';
                      document.getElementById('overview').hidden = !overview;
                      document.getElementById('jsonPanel').hidden = overview;
                      document.getElementById('tabOverview').setAttribute('aria-selected', String(overview));
                      document.getElementById('tabJson').setAttribute('aria-selected', String(!overview));
                    }

                    async function applyAndRender() {
                      const selected = [...document.querySelectorAll('#channels input:checked')].map(input => input.value);
                      const from = Number(document.getElementById('from').value);
                      const to = Number(document.getElementById('to').value);
                      const response = await fetch('/api/filter', {
                        method: 'POST',
                        headers: {'Content-Type': 'application/json'},
                        body: JSON.stringify({
                          filters: {channel: selected, week: {from, to}},
                          drilldownTileId: 'chart-revenue-channel',
                          drilldownValue: selected[0] || ''
                        })
                      });
                      state.filterResult = await response.json();
                      const dashboardResponse = await fetch('/api/dashboard');
                      state.dashboard = await dashboardResponse.json();
                      render();
                    }

                    document.getElementById('refresh').onclick = loadDashboard;
                    document.getElementById('upload').onclick = uploadDataset;
                    document.getElementById('ask').onclick = askQuestion;
                    document.getElementById('apply').onclick = applyAndRender;
                    document.getElementById('applyTop').onclick = applyAndRender;
                    document.getElementById('tabOverview').onclick = () => switchTab('overview');
                    document.getElementById('tabJson').onclick = () => switchTab('json');
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

    private static Object parseJson(String json) {
        try {
            return MAPPER.readValue(json, Object.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("failed to parse analytics result", e);
        }
    }

    private static final class DashboardWorkspace implements AutoCloseable {
        private AnalyticsDatabase analytics;
        private Path csv;
        private Path database;
        private Path dashboardSpec;
        private Path dashboardHtml;
        private boolean ownsAnalytics;

        private DashboardWorkspace(
                AnalyticsDatabase analytics,
                Path csv,
                Path database,
                Path dashboardSpec,
                Path dashboardHtml,
                boolean ownsAnalytics) {
            this.analytics = analytics;
            this.csv = csv;
            this.database = database;
            this.dashboardSpec = dashboardSpec;
            this.dashboardHtml = dashboardHtml;
            this.ownsAnalytics = ownsAnalytics;
        }

        synchronized String dashboardJson() {
            try {
                return Files.readString(dashboardSpec, StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new IllegalStateException("failed to read dashboard spec", e);
            }
        }

        synchronized String sessionJson() {
            return toJson(Map.of(
                    "csv", csv.toString(),
                    "database", database.toString(),
                    "dashboardSpec", dashboardSpec.toString(),
                    "dashboardHtml", dashboardHtml.toString(),
                    "rows", analytics.rowCount()));
        }

        synchronized String filterOptionsJson() {
            return toJson(Map.of(
                    "filters", List.of(
                            Map.of(
                                    "id", "channel",
                                    "label", "Channel",
                                    "type", "multi_select",
                                    "field", "channel",
                                    "values", valuesFrom(analytics.distinctValuesJson("channel", 50))),
                            Map.of(
                                    "id", "week",
                                    "label", "Week Range",
                                    "type", "range",
                                    "field", "week",
                                    "range", parseJson(analytics.numericRangeJson("week"))))));
        }

        synchronized String schemaJson() {
            return analytics.schemaJson();
        }

        synchronized String profileJson() {
            return analytics.profileJson();
        }

        synchronized String qualityJson() {
            return analytics.dataQualityJson();
        }

        synchronized String sampleJson() {
            return analytics.sqlJson("select * from data order by _row_id limit 25");
        }

        synchronized AnalyticsDashboard.DashboardFilterResult applyFilters(
                Map<String, Object> filters,
                String drilldownTileId,
                String drilldownValue) {
            return AnalyticsDashboard.applyFilters(
                    analytics, dashboardSpec, dashboardHtml, filters, drilldownTileId, drilldownValue);
        }

        synchronized String upload(UploadRequest request) {
            try {
                Path uploadedCsv = Files.createTempFile("java-ai-agent-upload-", "-" + safeName(request.filename()));
                Path uploadedDatabase = Files.createTempFile("java-ai-agent-upload-", ".sqlite");
                Path uploadedSpec = Files.createTempFile("java-ai-agent-upload-dashboard-", ".json");
                Path uploadedHtml = Files.createTempFile("java-ai-agent-upload-dashboard-", ".html");
                Files.writeString(uploadedCsv, request.content(), StandardCharsets.UTF_8);
                AnalyticsFrame frame = AnalyticsFrame.loadCsv(uploadedCsv);
                AnalyticsDatabase nextAnalytics = AnalyticsDatabase.importCsv(uploadedDatabase, uploadedCsv);
                AnalyticsDashboard.create(nextAnalytics, frame, uploadedSpec, uploadedHtml);
                replace(nextAnalytics, uploadedCsv, uploadedDatabase, uploadedSpec, uploadedHtml);
                return toJson(Map.of(
                        "status", "uploaded",
                        "filename", request.filename(),
                        "rows", analytics.rowCount(),
                        "filterOptions", parseJson(filterOptionsJson()),
                        "dashboard", parseJson(dashboardJson())));
            } catch (RuntimeException | IOException e) {
                throw new IllegalStateException("failed to upload CSV for dashboard: " + e.getMessage(), e);
            }
        }

        synchronized String chat(ChatRequest request) {
            String question = request.question().strip();
            String lower = question.toLowerCase(Locale.ROOT);
            try {
                String tool;
                String answer;
                String result;
                if (lower.contains("schema") || lower.contains("columns")) {
                    tool = "schema_summary";
                    result = analytics.schemaJson();
                    answer = "I inspected the active CSV schema and inferred column types.";
                } else if (lower.contains("quality") || lower.contains("missing") || lower.contains("duplicate")) {
                    tool = "data_quality_scan";
                    result = analytics.dataQualityJson();
                    answer = "I scanned missingness, cardinality, and duplicate row groups.";
                } else if (lower.contains("correlation") || lower.contains("correlate")) {
                    String target = targetColumn(lower);
                    tool = "correlation_analysis";
                    result = analytics.correlationsJson(target);
                    answer = "I computed numeric correlations against " + target + ".";
                } else if (lower.contains("feature") || lower.contains("driver") || lower.contains("important")) {
                    String target = targetColumn(lower);
                    tool = "interesting_features";
                    result = analytics.interestingFeaturesJson(target);
                    answer = "I ranked numeric and categorical features for " + target + ".";
                } else if (lower.contains("cause") || lower.contains("causal")) {
                    tool = "causal_sanity_check";
                    result = analytics.causalCheckJson("discount_rate", "revenue");
                    answer = "I checked the observational causal caveat for discount_rate and revenue.";
                } else if (lower.contains("forecast")) {
                    tool = "forecast_timeseries";
                    result = analytics.forecastJson("week", targetColumn(lower), 4);
                    answer = "I generated a simple trend forecast.";
                } else if (lower.contains("anomaly") || lower.contains("outlier")) {
                    tool = lower.contains("outlier") ? "outlier_scan" : "anomaly_detection";
                    result = lower.contains("outlier")
                            ? analytics.outliersJson(targetColumn(lower))
                            : analytics.anomalyJson(targetColumn(lower));
                    answer = "I scanned " + targetColumn(lower) + " for unusual values.";
                } else if (lower.contains("regression") || lower.contains("model")) {
                    tool = "regression_fit";
                    result = analytics.regressionJson("revenue", List.of("visitors", "orders", "ad_spend"));
                    answer = "I ran the regression feature screen with diagnostics and model card.";
                } else if (lower.contains("channel") || lower.contains("segment") || lower.contains("average")) {
                    tool = "sql_query";
                    result = analytics.sqlJson("select channel, round(avg(revenue), 2) as avg_revenue, "
                            + "round(avg(conversion_rate), 3) as avg_conversion_rate, count(*) as rows "
                            + "from data group by channel order by avg_revenue desc");
                    answer = "I grouped revenue and conversion by channel.";
                } else {
                    tool = "dataset_profile";
                    result = analytics.profileJson();
                    answer = "I profiled the active dataset. Ask about correlations, features, causality, "
                            + "anomalies, forecast, regression, or channel performance for a deeper pass.";
                }
                String model = "local_tool_router";
                Optional<String> ollamaAnswer = ollamaAnswer(question, answer, tool, result);
                if (ollamaAnswer.isPresent()) {
                    answer = ollamaAnswer.get();
                    model = "ollama:" + OLLAMA_MODEL;
                }
                return toJson(Map.of(
                        "question", question,
                        "answer", answer,
                        "model", model,
                        "tool", tool,
                        "result", parseJson(result)));
            } catch (RuntimeException e) {
                return toJson(Map.of(
                        "question", question,
                        "answer", "I could not answer that with the active dataset schema.",
                        "error", e.getMessage()));
            }
        }

        private void replace(
                AnalyticsDatabase nextAnalytics,
                Path nextCsv,
                Path nextDatabase,
                Path nextSpec,
                Path nextHtml) {
            if (ownsAnalytics) {
                analytics.close();
            }
            analytics = nextAnalytics;
            csv = nextCsv;
            database = nextDatabase;
            dashboardSpec = nextSpec;
            dashboardHtml = nextHtml;
            ownsAnalytics = true;
        }

        @Override
        public synchronized void close() {
            if (ownsAnalytics) {
                analytics.close();
                ownsAnalytics = false;
            }
        }

        private static String safeName(String filename) {
            String safe = filename.replaceAll("[^a-zA-Z0-9._-]", "_");
            return safe.isBlank() ? "uploaded.csv" : safe;
        }

        private static String targetColumn(String lower) {
            if (lower.contains("conversion")) {
                return "conversion_rate";
            }
            if (lower.contains("order")) {
                return "orders";
            }
            if (lower.contains("visitor") || lower.contains("traffic")) {
                return "visitors";
            }
            if (lower.contains("spend")) {
                return "ad_spend";
            }
            return "revenue";
        }

        @SuppressWarnings("unchecked")
        private static List<Object> valuesFrom(String distinctValuesJson) {
            Object parsed = parseJson(distinctValuesJson);
            if (parsed instanceof Map<?, ?> map && map.get("values") instanceof List<?> values) {
                return (List<Object>) values;
            }
            return List.of();
        }

        private static Optional<String> ollamaAnswer(
                String question,
                String fallbackAnswer,
                String tool,
                String resultJson) {
            String baseUrl = System.getenv("OLLAMA_BASE_URL");
            if (baseUrl == null || baseUrl.isBlank()) {
                return Optional.empty();
            }
            String prompt = """
                    You are an analytics assistant. Use only the provided tool result.
                    Keep the answer concise, practical, and cautious about causation.

                    Question:
                    %s

                    Tool:
                    %s

                    Draft answer:
                    %s

                    Tool result JSON:
                    %s
                    """.formatted(question, tool, fallbackAnswer, resultJson);
            try {
                String body = toJson(Map.of(
                        "model", OLLAMA_MODEL,
                        "stream", false,
                        "messages", List.of(
                                Map.of(
                                        "role", "system",
                                        "content", "Reply as a careful analytics copilot."),
                                Map.of("role", "user", "content", prompt))));
                HttpRequest request = HttpRequest.newBuilder(
                                URI.create(stripTrailingSlash(baseUrl) + "/api/chat"))
                        .timeout(Duration.ofMinutes(2))
                        .header("content-type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build();
                HttpResponse<String> response = HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(10))
                        .build()
                        .send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    return Optional.empty();
                }
                String content = MAPPER.readTree(response.body()).path("message").path("content").asText();
                return content.isBlank() ? Optional.empty() : Optional.of(content);
            } catch (IOException | InterruptedException e) {
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                return Optional.empty();
            }
        }

        private static String stripTrailingSlash(String value) {
            String stripped = value.strip();
            while (stripped.endsWith("/")) {
                stripped = stripped.substring(0, stripped.length() - 1);
            }
            return stripped;
        }
    }

    private record FilterRequest(Map<String, Object> filters, String drilldownTileId, String drilldownValue) {}

    private record UploadRequest(String filename, String content) {}

    private record ChatRequest(String question) {}
}
