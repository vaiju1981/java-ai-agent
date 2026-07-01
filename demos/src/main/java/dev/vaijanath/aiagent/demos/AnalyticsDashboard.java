package dev.vaijanath.aiagent.demos;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class AnalyticsDashboard {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    private AnalyticsDashboard() {}

    static DashboardArtifact create(
            AnalyticsDatabase analytics, AnalyticsFrame frame, Path specOutput, Path htmlOutput) {
        Map<String, Object> spec = buildSpec(analytics, frame);
        writeJson(specOutput, spec);
        writeHtml(htmlOutput, spec);
        return new DashboardArtifact(specOutput, htmlOutput);
    }

    static DashboardUpdate update(
            Path specOutput,
            Path htmlOutput,
            String operation,
            String tileId,
            String title,
            String body,
            List<String> evidenceIds) {
        Map<String, Object> spec = readSpec(specOutput);
        String normalizedOperation = operation == null || operation.isBlank() ? "add_insight_tile" : operation;
        String normalizedTileId = tileId == null || tileId.isBlank() ? "insight-followup" : tileId;
        switch (normalizedOperation) {
            case "add_insight_tile" -> addInsightTile(spec, normalizedTileId, title, body, evidenceIds);
            case "remove_tile" -> removeTile(spec, normalizedTileId);
            case "rename_tile" -> renameTile(spec, normalizedTileId, title);
            default -> throw new IllegalArgumentException("unsupported dashboard update operation: " + operation);
        }
        int revision = incrementRevision(spec);
        appendChange(spec, normalizedOperation, normalizedTileId);
        writeJson(specOutput, spec);
        writeHtml(htmlOutput, spec);
        return new DashboardUpdate(specOutput, htmlOutput, normalizedOperation, normalizedTileId, revision);
    }

    static DashboardFilterResult applyFilters(
            AnalyticsDatabase analytics,
            Path specOutput,
            Path htmlOutput,
            Map<String, Object> filters,
            String drilldownTileId,
            String drilldownValue) {
        Map<String, Object> spec = readSpec(specOutput);
        String where = filterWhere(filters);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("appliedAt", Instant.now().toString());
        result.put("filters", filters == null ? Map.of() : filters);
        result.put("where", where.isBlank() ? "all rows" : where);
        result.put("tiles", List.of(
                Map.of(
                        "tileId", "kpi-rows",
                        "data", parse(analytics.sqlJson("select count(*) as rows from data" + where))),
                Map.of(
                        "tileId", "kpi-revenue",
                        "data", parse(analytics.sqlJson(
                                "select round(sum(revenue), 2) as total_revenue from data" + where))),
                Map.of(
                        "tileId", "kpi-conversion",
                        "data", parse(analytics.sqlJson(
                                "select round(avg(conversion_rate), 3) as avg_conversion_rate from data" + where))),
                Map.of(
                        "tileId", "chart-revenue-channel",
                        "data", parse(analytics.sqlJson("select channel, round(avg(revenue), 2) as avg_revenue, "
                                + "round(avg(conversion_rate), 3) as avg_conversion_rate, count(*) as rows "
                                + "from data" + where + " group by channel order by avg_revenue desc"))),
                Map.of(
                        "tileId", "table-channel",
                        "data", parse(analytics.sqlJson("select channel, round(avg(revenue), 2) as avg_revenue, "
                                + "round(sum(revenue), 2) as total_revenue, count(*) as rows "
                                + "from data" + where + " group by channel order by total_revenue desc")))));
        if (drilldownTileId != null && !drilldownTileId.isBlank()
                && drilldownValue != null && !drilldownValue.isBlank()) {
            result.put("drilldown", Map.of(
                    "tileId", drilldownTileId,
                    "value", drilldownValue,
                    "data", parse(analytics.sqlJson("select week, channel, visitors, orders, revenue, "
                            + "conversion_rate from data" + appendCondition(where, "channel = '"
                            + escapeSql(drilldownValue) + "'") + " order by week"))));
        }
        spec.put("lastFilterResult", result);
        spec.put("updatedAt", Instant.now().toString());
        writeJson(specOutput, spec);
        writeHtml(htmlOutput, spec);
        return new DashboardFilterResult(specOutput, htmlOutput, result);
    }

    private static Map<String, Object> buildSpec(AnalyticsDatabase analytics, AnalyticsFrame frame) {
        JsonNode totals = firstRow(analytics.sqlJson(
                "select count(*) as rows, round(sum(revenue), 2) as total_revenue, "
                        + "round(avg(conversion_rate), 3) as avg_conversion_rate, "
                        + "round(avg(ad_spend), 2) as avg_ad_spend from data"));
        JsonNode topChannel = firstRow(analytics.sqlJson(
                "select channel, round(avg(revenue), 2) as avg_revenue from data "
                        + "group by channel order by avg_revenue desc limit 1"));
        Object channel = topChannel.path("channel").asText("unknown");
        Object topRevenue = topChannel.path("avg_revenue").isNumber()
                ? topChannel.path("avg_revenue").numberValue()
                : topChannel.path("avg_revenue").asText();

        Map<String, Object> dashboard = new LinkedHashMap<>();
        dashboard.put("version", 1);
        dashboard.put("title", "Analytics Insight Dashboard");
        dashboard.put("generatedAt", Instant.now().toString());
        dashboard.put("source", Map.of("table", "data", "engine", "sqlite+tablesaw"));
        dashboard.put("filters", List.of(
                Map.of(
                        "id", "channel",
                        "label", "Channel",
                        "type", "multi_select",
                        "field", "channel",
                        "default", "all",
                        "drilldownField", "channel"),
                Map.of(
                        "id", "week",
                        "label", "Week",
                        "type", "range",
                        "field", "week",
                        "default", Map.of("from", 1, "to", 16))));
        dashboard.put("layout", List.of(
                Map.of("tileId", "kpi-rows", "x", 0, "y", 0, "w", 1, "h", 1),
                Map.of("tileId", "kpi-revenue", "x", 1, "y", 0, "w", 1, "h", 1),
                Map.of("tileId", "kpi-conversion", "x", 2, "y", 0, "w", 1, "h", 1),
                Map.of("tileId", "chart-revenue-channel", "x", 0, "y", 1, "w", 2, "h", 2),
                Map.of("tileId", "table-channel", "x", 2, "y", 1, "w", 1, "h", 2),
                Map.of("tileId", "insight-causal", "x", 0, "y", 3, "w", 3, "h", 1)));
        dashboard.put("tiles", List.of(
                kpi("kpi-rows", "Rows", totals.path("rows").asLong(), "Imported records", "dataset_profile"),
                kpi(
                        "kpi-revenue",
                        "Total Revenue",
                        totals.path("total_revenue").numberValue(),
                        "Sum of revenue",
                        "sql_query"),
                kpi(
                        "kpi-conversion",
                        "Avg Conversion",
                        totals.path("avg_conversion_rate").numberValue(),
                        "Average conversion_rate",
                        "sql_query"),
                Map.of(
                        "id", "chart-revenue-channel",
                        "type", "chart",
                        "title", "Revenue By Channel",
                        "chart", parse(frame.chartSpecJson("bar", "channel", "revenue")),
                        "drilldown", Map.of(
                                "type", "sql",
                                "label", "Inspect channel rows",
                                "queryTemplate",
                                        "select * from data where channel = :channel order by week")),
                Map.of(
                        "id", "table-channel",
                        "type", "table",
                        "title", "Channel Rollup",
                        "data", parse(frame.groupByJson("channel", "revenue", "avg")),
                        "drilldown", Map.of(
                                "type", "tool",
                                "tool", "distribution_compare",
                                "arguments", Map.of("segment", "channel", "target", "revenue"))),
                Map.of(
                        "id", "insight-causal",
                        "type", "insight",
                        "title", "Decision Caveat",
                        "body",
                                "Top revenue channel is " + channel + " with average revenue " + topRevenue
                                        + ". Discount-rate relationships remain observational; require an "
                                        + "experiment or stronger causal design before acting as a causal lever.",
                        "evidence", List.of("causal_sanity_check", "semantic_query_plan", "sql_query"))));
        return dashboard;
    }

    private static Map<String, Object> kpi(
            String id, String title, Object value, String subtitle, String evidenceTool) {
        return Map.of(
                "id", id,
                "type", "kpi",
                "title", title,
                "value", value,
                "subtitle", subtitle,
                "evidence", List.of(evidenceTool),
                "drilldown", Map.of("type", "tool", "tool", "column_profile"));
    }

    @SuppressWarnings("unchecked")
    private static void addInsightTile(
            Map<String, Object> spec, String tileId, String title, String body, List<String> evidenceIds) {
        List<Map<String, Object>> tiles = (List<Map<String, Object>>) spec.get("tiles");
        removeTile(spec, tileId);
        Map<String, Object> tile = new LinkedHashMap<>();
        tile.put("id", tileId);
        tile.put("type", "insight");
        tile.put("title", title == null || title.isBlank() ? "Analyst Follow-up" : title);
        tile.put("body", body == null || body.isBlank() ? "No body supplied." : body);
        tile.put("evidenceIds", evidenceIds == null ? List.of() : List.copyOf(evidenceIds));
        tile.put("drilldown", Map.of("type", "evidence_manifest", "label", "Open supporting evidence"));
        tiles.add(tile);

        List<Map<String, Object>> layout = (List<Map<String, Object>>) spec.get("layout");
        int y = layout.stream()
                .mapToInt(entry -> ((Number) entry.get("y")).intValue() + ((Number) entry.get("h")).intValue())
                .max()
                .orElse(0);
        layout.add(Map.of("tileId", tileId, "x", 0, "y", y, "w", 3, "h", 1));
    }

    @SuppressWarnings("unchecked")
    private static void removeTile(Map<String, Object> spec, String tileId) {
        List<Map<String, Object>> tiles = (List<Map<String, Object>>) spec.get("tiles");
        tiles.removeIf(tile -> tileId.equals(tile.get("id")));
        List<Map<String, Object>> layout = (List<Map<String, Object>>) spec.get("layout");
        layout.removeIf(entry -> tileId.equals(entry.get("tileId")));
    }

    @SuppressWarnings("unchecked")
    private static void renameTile(Map<String, Object> spec, String tileId, String title) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("title is required for rename_tile");
        }
        List<Map<String, Object>> tiles = (List<Map<String, Object>>) spec.get("tiles");
        for (Map<String, Object> tile : tiles) {
            if (tileId.equals(tile.get("id"))) {
                tile.put("title", title);
                return;
            }
        }
        throw new IllegalArgumentException("unknown dashboard tile: " + tileId);
    }

    @SuppressWarnings("unchecked")
    private static void appendChange(Map<String, Object> spec, String operation, String tileId) {
        List<Map<String, Object>> changes = (List<Map<String, Object>>) spec.computeIfAbsent(
                "changeLog", ignored -> new ArrayList<Map<String, Object>>());
        changes.add(Map.of(
                "operation", operation,
                "tileId", tileId,
                "at", Instant.now().toString()));
    }

    private static int incrementRevision(Map<String, Object> spec) {
        int revision = spec.get("revision") instanceof Number number ? number.intValue() + 1 : 2;
        spec.put("revision", revision);
        spec.put("updatedAt", Instant.now().toString());
        return revision;
    }

    @SuppressWarnings("unchecked")
    private static String filterWhere(Map<String, Object> filters) {
        if (filters == null || filters.isEmpty()) {
            return "";
        }
        List<String> conditions = new ArrayList<>();
        Object channels = filters.get("channel");
        if (channels instanceof List<?> values && !values.isEmpty()) {
            String in = values.stream()
                    .map(value -> "'" + escapeSql(String.valueOf(value)) + "'")
                    .reduce((a, b) -> a + ", " + b)
                    .orElse("");
            conditions.add("channel in (" + in + ")");
        } else if (channels instanceof String channel && !channel.isBlank() && !"all".equalsIgnoreCase(channel)) {
            conditions.add("channel = '" + escapeSql(channel) + "'");
        }
        Object week = filters.get("week");
        if (week instanceof Map<?, ?> weekRange) {
            Object from = weekRange.get("from");
            Object to = weekRange.get("to");
            if (from instanceof Number fromNumber) {
                conditions.add("week >= " + fromNumber.intValue());
            }
            if (to instanceof Number toNumber) {
                conditions.add("week <= " + toNumber.intValue());
            }
        }
        return conditions.isEmpty() ? "" : " where " + String.join(" and ", conditions);
    }

    private static String appendCondition(String where, String condition) {
        return where == null || where.isBlank() ? " where " + condition : where + " and " + condition;
    }

    private static JsonNode firstRow(String sqlJson) {
        JsonNode rows = parseNode(sqlJson).path("rows");
        if (!rows.isArray() || rows.isEmpty()) {
            throw new IllegalStateException("dashboard query returned no rows");
        }
        return rows.get(0);
    }

    private static Object parse(String json) {
        try {
            return MAPPER.readValue(json, Object.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("failed to parse dashboard source JSON", e);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> readSpec(Path specOutput) {
        if (!Files.exists(specOutput)) {
            throw new IllegalStateException("dashboard spec does not exist; run dashboard_create first");
        }
        try {
            return MAPPER.readValue(Files.readString(specOutput, StandardCharsets.UTF_8), LinkedHashMap.class);
        } catch (IOException e) {
            throw new IllegalStateException("failed to read dashboard spec", e);
        }
    }

    private static JsonNode parseNode(String json) {
        try {
            return MAPPER.readTree(json);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("failed to parse dashboard source JSON", e);
        }
    }

    private static void writeJson(Path output, Object value) {
        try {
            Files.writeString(output, MAPPER.writeValueAsString(value), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("failed to write dashboard spec", e);
        }
    }

    private static void writeHtml(Path output, Map<String, Object> spec) {
        String html = """
                <!doctype html>
                <html lang="en">
                <head>
                  <meta charset="utf-8">
                  <title>Analytics Insight Dashboard</title>
                  <style>
                    body { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif; margin: 32px; color: #1f2937; }
                    h1 { margin-bottom: 4px; }
                    .grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 16px; }
                    .tile { border: 1px solid #d1d5db; border-radius: 8px; padding: 16px; background: #fff; }
                    .wide { grid-column: span 2; }
                    .full { grid-column: 1 / -1; }
                    pre { white-space: pre-wrap; background: #f9fafb; padding: 12px; border-radius: 6px; overflow: auto; }
                  </style>
                </head>
                <body>
                  <h1>%s</h1>
                  <p>Rendered from a reusable dashboard object model with filters, tiles, charts, and drilldowns.</p>
                  <div class="grid">
                    <section class="tile"><h2>Filters</h2><pre>%s</pre></section>
                    <section class="tile wide"><h2>Layout</h2><pre>%s</pre></section>
                    <section class="tile full"><h2>Tiles</h2><pre>%s</pre></section>
                    <section class="tile full"><h2>Last Filter Result</h2><pre>%s</pre></section>
                  </div>
                </body>
                </html>
                """.formatted(
                escapeHtml(String.valueOf(spec.get("title"))),
                escapeHtml(toJson(spec.get("filters"))),
                escapeHtml(toJson(spec.get("layout"))),
                escapeHtml(toJson(spec.get("tiles"))),
                escapeHtml(toJson(spec.getOrDefault("lastFilterResult", Map.of()))));
        try {
            Files.writeString(output, html, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("failed to write dashboard", e);
        }
    }

    private static String toJson(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("failed to serialize dashboard", e);
        }
    }

    private static String escapeHtml(String value) {
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private static String escapeSql(String value) {
        return value.replace("'", "''");
    }

    record DashboardArtifact(Path spec, Path html) {}

    record DashboardUpdate(Path spec, Path html, String operation, String tileId, int revision) {}

    record DashboardFilterResult(Path spec, Path html, Map<String, Object> result) {}
}
