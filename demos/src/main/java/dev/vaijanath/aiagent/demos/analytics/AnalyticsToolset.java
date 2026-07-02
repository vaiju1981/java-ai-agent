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
import java.util.Map;

/**
 * Generic, read-only analytics tools for the agent. Nothing is dataset-specific: the metric and
 * dimension vocabulary comes from the generated {@link SemanticModel}, and queries are planned
 * (fan-out-safe) by {@link QueryPlanner}. The agent learns the vocabulary via describe_dataset.
 */
final class AnalyticsToolset {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final CsvSource source;
    private final SemanticModel model;

    AnalyticsToolset(CsvSource source) {
        this(source, SemanticModel.fromProfile(DatasetProfiler.profile(source)));
    }

    AnalyticsToolset(CsvSource source, SemanticModel model) {
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
                        "Aggregate one or more metrics grouped by zero or more dimensions. Use exact refs from "
                                + "describe_dataset (metric e.g. 'orders.revenue', dimension e.g. 'customers.segment'). "
                                + "Joins are planned automatically and are fan-out-safe.",
                        "{\"type\":\"object\",\"properties\":{"
                                + "\"metrics\":{\"type\":\"array\",\"items\":{\"type\":\"string\"}},"
                                + "\"dimensions\":{\"type\":\"array\",\"items\":{\"type\":\"string\"}}},"
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
                    String sql = QueryPlanner.plan(model, new QueryPlanner.QuerySpec(metrics, dimensions));
                    List<Map<String, Object>> rows = source.nativeQuery(sql);
                    return ToolResult.ok(MAPPER.writeValueAsString(Map.of("sql", sql, "rows", rows)));
                } catch (RuntimeException | JsonProcessingException e) {
                    return ToolResult.error(String.valueOf(e.getMessage()));
                }
            }
        };
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
