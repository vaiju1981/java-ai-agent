package dev.vaijanath.aiagent.demos.analytics;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Query-oriented model generated from a {@link DatasetProfile}. Nothing here is hardcoded to a
 * dataset: measures become metrics (additive -> sum, ratio -> avg), dimensions/dates/booleans
 * become groupable dimensions, identifiers become entity keys used for joins, and date/timestamp
 * columns are also surfaced as {@code timeColumns} for time-window filtering.
 */
record SemanticModel(
        List<Metric> metrics,
        List<Dimension> dimensions,
        List<String> entityKeys,
        List<String> timeColumns,
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
        List<String> timeColumns = new ArrayList<>();
        for (TableSchema table : profile.tables()) {
            for (ColumnSchema column : table.columns()) {
                switch (column.role()) {
                    case MEASURE_ADDITIVE -> metrics.add(new Metric(table.name(), column.name(), "sum"));
                    case MEASURE_RATIO -> metrics.add(new Metric(table.name(), column.name(), "avg"));
                    case DATE, TIMESTAMP -> {
                        dimensions.add(new Dimension(table.name(), column.name()));
                        timeColumns.add(table.name() + "." + column.name());
                    }
                    case DIMENSION, BOOLEAN -> dimensions.add(new Dimension(table.name(), column.name()));
                    case IDENTIFIER -> entityKeys.add(table.name() + "." + column.name());
                    default -> { /* TEXT / UNKNOWN: not directly queryable */ }
                }
            }
        }
        return new SemanticModel(metrics, dimensions, entityKeys, timeColumns, profile.relationships());
    }

    String toJson() {
        try {
            return MAPPER.writeValueAsString(Map.of(
                    "metrics", metrics.stream().map(m -> Map.of(
                            "ref", m.ref(), "table", m.table(),
                            "column", m.column(), "aggregation", m.aggregation())).toList(),
                    "dimensions", dimensions.stream().map(d -> Map.of(
                            "ref", d.ref(), "table", d.table(), "column", d.column())).toList(),
                    "entityKeys", entityKeys,
                    "timeColumns", timeColumns,
                    "relationships", relationships));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("failed to serialize SemanticModel", e);
        }
    }
}
