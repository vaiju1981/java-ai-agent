package dev.vaijanath.aiagent.demos.analytics;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.vaijanath.aiagent.tool.Tool;
import dev.vaijanath.aiagent.tool.ToolEffect;
import dev.vaijanath.aiagent.tool.ToolResult;
import dev.vaijanath.aiagent.tool.ToolSpec;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Generic, read-only analytics tools for the agent. Nothing is dataset-specific: the metric and
 * dimension vocabulary comes from the generated {@link SemanticModel}, and queries are planned
 * (fan-out-safe) by {@link QueryPlanner}. The agent learns the vocabulary via describe_dataset.
 */
final class AnalyticsToolset {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final DataSource source;
    private final SemanticModel model;

    AnalyticsToolset(DataSource source) {
        this(source, SemanticModel.fromProfile(DatasetProfiler.profile(source)));
    }

    AnalyticsToolset(DataSource source, SemanticModel model) {
        this.source = source;
        this.model = model;
    }

    SemanticModel model() {
        return model;
    }

    Tool describeDataset() {
        return new Tool() {
            @Override
            public ToolSpec spec() {
                return new ToolSpec(
                        "describe_dataset",
                        "List the queryable metrics, dimensions, entity keys, and table relationships of the "
                                + "active dataset. Call this first; use the exact 'ref' values it returns when "
                                + "calling run_query.",
                        "{\"type\":\"object\",\"properties\":{}}",
                        ToolEffect.READ_ONLY);
            }

            @Override
            public ToolResult invoke(String argumentsJson) {
                try {
                    // Compact catalog: scannable refs, small enough for wide (100+ column) datasets.
                    Map<String, Object> catalog = Map.of(
                            "metrics", model.metrics().stream()
                                    .map(SemanticModel.Metric::ref).toList(),
                            "dimensions", model.dimensions().stream()
                                    .map(SemanticModel.Dimension::ref).toList(),
                            "timeColumns", model.timeColumns(),
                            "tables", source.tables().stream().map(t -> Map.of(
                                    "name", t.name(),
                                    "columns", t.columns().stream()
                                            .map(c -> c.name() + " " + c.physicalType()).toList())).toList(),
                            "relationships", model.relationships().stream()
                                    .map(r -> r.fromTable() + "." + r.fromColumn() + " ~ "
                                            + r.toTable() + "." + r.toColumn() + " (" + r.cardinality() + ")")
                                    .toList());
                    return ToolResult.ok(MAPPER.writeValueAsString(catalog));
                } catch (JsonProcessingException e) {
                    return ToolResult.error(String.valueOf(e.getMessage()));
                }
            }
        };
    }

    Tool runQuery() {
        return new Tool() {
            @Override
            public ToolSpec spec() {
                return new ToolSpec(
                        "run_query",
                        "Aggregate one or more metrics grouped by zero or more dimensions, with optional filters "
                                + "and a time window. Use exact refs from describe_dataset. For a period like "
                                + "'last 90 days', set lastDays=90 and timeColumn to a ref from the dataset's "
                                + "timeColumns. For other constraints use filters, each {column, op, value} with op "
                                + "one of = != < <= > >=. For 'top N', set limit=N (optionally orderBy to a metric "
                                + "ref; defaults to the first metric, descending). Joins are fan-out-safe.",
                        "{\"type\":\"object\",\"properties\":{"
                                + "\"metrics\":{\"type\":\"array\",\"items\":{\"type\":\"string\"}},"
                                + "\"dimensions\":{\"type\":\"array\",\"items\":{\"type\":\"string\"}},"
                                + "\"filters\":{\"type\":\"array\",\"items\":{\"type\":\"object\",\"properties\":{"
                                + "\"column\":{\"type\":\"string\"},\"op\":{\"type\":\"string\"},"
                                + "\"value\":{\"type\":\"string\"}}}},"
                                + "\"lastDays\":{\"type\":\"integer\"},"
                                + "\"timeColumn\":{\"type\":\"string\"},"
                                + "\"orderBy\":{\"type\":\"string\"},"
                                + "\"descending\":{\"type\":\"boolean\"},"
                                + "\"limit\":{\"type\":\"integer\"}},"
                                + "\"required\":[\"metrics\"]}",
                        ToolEffect.READ_ONLY);
            }

            @Override
            public ToolResult invoke(String argumentsJson) {
                try {
                    JsonNode node = MAPPER.readTree(
                            argumentsJson == null || argumentsJson.isBlank() ? "{}" : argumentsJson);
                    List<String> metrics = toList(node.get("metrics"));
                    List<String> dimensions = toList(node.get("dimensions"));
                    List<QueryPlanner.Filter> filters = toFilters(node.get("filters"));
                    Integer lastDays = node.hasNonNull("lastDays") ? node.get("lastDays").asInt() : null;
                    String timeColumn = node.hasNonNull("timeColumn") ? node.get("timeColumn").asText() : null;
                    String orderBy = node.hasNonNull("orderBy") ? node.get("orderBy").asText() : null;
                    boolean descending = !node.hasNonNull("descending") || node.get("descending").asBoolean();
                    Integer limit = node.hasNonNull("limit") ? node.get("limit").asInt() : null;
                    String sql = QueryPlanner.plan(model, new QueryPlanner.QuerySpec(
                            metrics, dimensions, filters, lastDays, timeColumn, orderBy, descending, limit));
                    List<Map<String, Object>> rows = source.nativeQuery(sql);
                    return ToolResult.ok(MAPPER.writeValueAsString(Map.of("sql", sql, "rows", rows)));
                } catch (RuntimeException | JsonProcessingException e) {
                    return ToolResult.error(String.valueOf(e.getMessage()));
                }
            }
        };
    }

    Tool runSql() {
        return new Tool() {
            @Override
            public ToolSpec spec() {
                return new ToolSpec(
                        "run_sql",
                        "Run a read-only DuckDB SQL SELECT for anything run_query cannot express: arbitrary WHERE "
                                + "filters, time spans (date_trunc, extract, INTERVAL, strftime), CASE, window "
                                + "functions, subqueries. Only a single SELECT/WITH is allowed. Use exact table and "
                                + "column names and types from describe_dataset ('tables'). When joining two fact "
                                + "tables, aggregate each to its key grain first to avoid double-counting.",
                        "{\"type\":\"object\",\"properties\":{\"sql\":{\"type\":\"string\"}},"
                                + "\"required\":[\"sql\"]}",
                        ToolEffect.READ_ONLY);
            }

            @Override
            public ToolResult invoke(String argumentsJson) {
                try {
                    JsonNode node = MAPPER.readTree(
                            argumentsJson == null || argumentsJson.isBlank() ? "{}" : argumentsJson);
                    String sql = node.path("sql").asText("").strip();
                    if (!isReadOnlySelect(sql)) {
                        return ToolResult.error("only a single read-only SELECT/WITH statement is allowed");
                    }
                    List<Map<String, Object>> rows = source.nativeQuery(sql, 1000);
                    return ToolResult.ok(MAPPER.writeValueAsString(
                            Map.of("sql", sql, "rows", rows, "rowLimit", 1000)));
                } catch (RuntimeException | JsonProcessingException e) {
                    return ToolResult.error(String.valueOf(e.getMessage()));
                }
            }
        };
    }

    private static final Set<String> SQL_FORBIDDEN = Set.of(
            "insert", "update", "delete", "drop", "alter", "create", "attach", "detach", "copy",
            "install", "load", "pragma", "export", "truncate", "vacuum", "grant", "revoke");

    static boolean isReadOnlySelect(String sql) {
        if (sql == null) {
            return false;
        }
        String s = sql.strip();
        if (s.endsWith(";")) {
            s = s.substring(0, s.length() - 1).strip();
        }
        if (s.isEmpty() || s.contains(";")) {
            return false; // single statement only
        }
        String lower = s.toLowerCase(Locale.ROOT);
        if (!lower.startsWith("select") && !lower.startsWith("with")) {
            return false;
        }
        for (String forbidden : SQL_FORBIDDEN) {
            if (Pattern.compile("\\b" + forbidden + "\\b").matcher(lower).find()) {
                return false;
            }
        }
        return true;
    }

    Tool correlate() {
        return new Tool() {
            @Override
            public ToolSpec spec() {
                return new ToolSpec(
                        "correlate",
                        "Pearson correlation between a target metric and the other numeric measures in its table, "
                                + "ranked by strength (1=strong positive, -1=strong negative). Arg: target = a "
                                + "metric ref (e.g. 'orders.revenue'). Correlation is not causation.",
                        "{\"type\":\"object\",\"properties\":{\"target\":{\"type\":\"string\"}},"
                                + "\"required\":[\"target\"]}",
                        ToolEffect.READ_ONLY);
            }

            @Override
            public ToolResult invoke(String argumentsJson) {
                try {
                    JsonNode node = MAPPER.readTree(
                            argumentsJson == null || argumentsJson.isBlank() ? "{}" : argumentsJson);
                    String targetRef = node.path("target").asText("").trim();
                    int annotation = targetRef.indexOf(" (");
                    if (annotation > 0) {
                        targetRef = targetRef.substring(0, annotation).trim();
                    }
                    final String ref = targetRef;
                    SemanticModel.Metric target = model.metrics().stream()
                            .filter(m -> m.ref().equals(ref)).findFirst()
                            .orElseThrow(() -> new IllegalArgumentException("unknown target metric: " + ref));
                    List<SemanticModel.Metric> others = model.metrics().stream()
                            .filter(m -> m.table().equals(target.table()) && !m.column().equals(target.column()))
                            .toList();
                    if (others.isEmpty()) {
                        return ToolResult.ok("{\"target\":\"" + ref + "\",\"correlations\":[]}");
                    }
                    List<String> selects = new ArrayList<>();
                    for (SemanticModel.Metric o : others) {
                        selects.add("corr(cast(" + Sql.quote(target.column()) + " as double), cast("
                                + Sql.quote(o.column()) + " as double)) as " + Sql.quote(o.column()));
                    }
                    Map<String, Object> row = source.nativeQuery(
                            "select " + String.join(", ", selects) + " from " + Sql.quote(target.table())).get(0);
                    List<Map<String, Object>> results = new ArrayList<>();
                    for (SemanticModel.Metric o : others) {
                        Object v = row.get(o.column());
                        if (v instanceof Number n && Double.isFinite(n.doubleValue())) {
                            results.add(Map.of("column", o.ref(),
                                    "correlation", Math.round(n.doubleValue() * 1000.0) / 1000.0));
                        }
                    }
                    results.sort((x, y) -> Double.compare(
                            Math.abs(((Number) y.get("correlation")).doubleValue()),
                            Math.abs(((Number) x.get("correlation")).doubleValue())));
                    return ToolResult.ok(MAPPER.writeValueAsString(
                            Map.of("target", target.ref(), "correlations", results)));
                } catch (RuntimeException | JsonProcessingException e) {
                    return ToolResult.error(String.valueOf(e.getMessage()));
                }
            }
        };
    }

    private static List<QueryPlanner.Filter> toFilters(JsonNode node) {
        List<QueryPlanner.Filter> out = new ArrayList<>();
        if (node != null && node.isArray()) {
            for (JsonNode f : node) {
                String column = f.path("column").asText("");
                String op = f.path("op").asText("");
                String value = f.path("value").asText("");
                int annotation = column.indexOf(" (");
                if (annotation > 0) {
                    column = column.substring(0, annotation);
                }
                if (!column.isBlank() && !op.isBlank()) {
                    out.add(new QueryPlanner.Filter(column.trim(), op.trim(), value));
                }
            }
        }
        return out;
    }

    private static List<String> toList(JsonNode node) {
        List<String> out = new ArrayList<>();
        if (node == null || node.isNull()) {
            return out;
        }
        if (node.isArray()) {
            node.forEach(item -> add(out, item.asText()));
        } else {
            // Be liberal: models often pass a scalar "a" or "a,b" instead of the ["a"] the schema asks for.
            for (String part : node.asText().split(",")) {
                add(out, part);
            }
        }
        return out;
    }

    private static void add(List<String> out, String value) {
        String trimmed = value == null ? "" : value.trim();
        // Tolerate refs the model annotates, e.g. "orders.revenue (sum)" -> "orders.revenue".
        int annotation = trimmed.indexOf(" (");
        if (annotation > 0) {
            trimmed = trimmed.substring(0, annotation).trim();
        }
        if (!trimmed.isEmpty()) {
            out.add(trimmed);
        }
    }
}
