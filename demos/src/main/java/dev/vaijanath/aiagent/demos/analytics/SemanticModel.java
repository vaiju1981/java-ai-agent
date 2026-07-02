package dev.vaijanath.aiagent.demos.analytics;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Query-oriented model generated from a {@link DatasetProfile}. Nothing here is hardcoded to a
 * dataset: measures become metrics (additive -> sum, ratio -> avg), dimensions/dates/booleans
 * become groupable dimensions, identifiers become entity keys used for joins.
 */
record SemanticModel(
        List<Metric> metrics,
        List<Dimension> dimensions,
        List<String> entityKeys,
        List<Relationship> relationships) {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    record Metric(String table, String column, String aggregation) {
        String ref() {
            return table + "." + column;
        }
    }

    record Dimension(String table, String column) {
        String ref() {
            return table + "." + column;
        }
    }

    static SemanticModel fromProfile(DatasetProfile profile) {
        List<Metric> metrics = new ArrayList<>();
        List<Dimension> dimensions = new ArrayList<>();
        List<String> entityKeys = new ArrayList<>();
        for (TableSchema table : profile.tables()) {
            for (ColumnSchema column : table.columns()) {
                switch (column.role()) {
                    case MEASURE_ADDITIVE -> metrics.add(new Metric(table.name(), column.name(), "sum"));
                    case MEASURE_RATIO -> metrics.add(new Metric(table.name(), column.name(), "avg"));
                    case DIMENSION, DATE, TIMESTAMP, BOOLEAN ->
                            dimensions.add(new Dimension(table.name(), column.name()));
                    case IDENTIFIER -> entityKeys.add(table.name() + "." + column.name());
                    default -> { /* TEXT / UNKNOWN: not directly queryable in M2 */ }
                }
            }
        }
        return new SemanticModel(metrics, dimensions, entityKeys, profile.relationships());
    }

    String toJson() {
        try {
            return MAPPER.writeValueAsString(Map.of(
                    "metrics", metrics,
                    "dimensions", dimensions,
                    "entityKeys", entityKeys,
                    "relationships", relationships));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("failed to serialize SemanticModel", e);
        }
    }
}
