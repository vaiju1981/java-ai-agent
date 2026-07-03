package dev.vaijanath.aiagent.demos.analytics;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Query-oriented model generated from a {@link DatasetProfile}. Everything is derived from the data,
 * not the file: measures become metrics (additive -> sum, ratio -> avg); dimensions/booleans become
 * groupable dimensions; identifiers become entity keys; and every date/timestamp column carries a
 * {@link TimeEncoding} so it can be normalized to a real timestamp regardless of how it was stored
 * (native DATE, epoch seconds/millis, or text) — so time windows work on any dataset.
 */
record SemanticModel(
        List<Metric> metrics,
        List<Dimension> dimensions,
        List<String> entityKeys,
        List<TimeColumn> timeColumns,
        List<Relationship> relationships) {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    enum TimeEncoding {
        NATIVE, EPOCH_SECONDS, EPOCH_MILLIS, TEXT
    }

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

    record TimeColumn(String table, String column, TimeEncoding encoding) {
        String ref() {
            return table + "." + column;
        }

        /** SQL that yields a real TIMESTAMP from this column, whatever its storage encoding. */
        String toTimestamp(String columnSql) {
            return switch (encoding) {
                case EPOCH_SECONDS -> "to_timestamp(" + columnSql + ")";
                case EPOCH_MILLIS -> "to_timestamp(" + columnSql + " / 1000)";
                case TEXT -> "try_cast(" + columnSql + " as timestamp)";
                case NATIVE -> columnSql;
            };
        }
    }

    static SemanticModel fromProfile(DatasetProfile profile) {
        Map<String, ColumnProfile> stats = new LinkedHashMap<>();
        for (ColumnProfile cp : profile.columns()) {
            stats.put(cp.table() + "." + cp.name(), cp);
        }
        List<Metric> metrics = new ArrayList<>();
        List<Dimension> dimensions = new ArrayList<>();
        List<String> entityKeys = new ArrayList<>();
        List<TimeColumn> timeColumns = new ArrayList<>();
        for (TableSchema table : profile.tables()) {
            for (ColumnSchema column : table.columns()) {
                switch (column.role()) {
                    case MEASURE_ADDITIVE -> metrics.add(new Metric(table.name(), column.name(), "sum"));
                    case MEASURE_RATIO -> metrics.add(new Metric(table.name(), column.name(), "avg"));
                    case DATE, TIMESTAMP -> {
                        dimensions.add(new Dimension(table.name(), column.name()));
                        timeColumns.add(new TimeColumn(table.name(), column.name(),
                                encodingOf(column, stats.get(table.name() + "." + column.name()))));
                    }
                    case DIMENSION, BOOLEAN -> dimensions.add(new Dimension(table.name(), column.name()));
                    case IDENTIFIER -> entityKeys.add(table.name() + "." + column.name());
                    default -> { /* TEXT / UNKNOWN: not directly queryable */ }
                }
            }
        }
        return new SemanticModel(metrics, dimensions, entityKeys, timeColumns, profile.relationships());
    }

    private static TimeEncoding encodingOf(ColumnSchema column, ColumnProfile stats) {
        String type = column.physicalType().toUpperCase(Locale.ROOT);
        if (type.contains("TIMESTAMP") || type.contains("DATE")) {
            return TimeEncoding.NATIVE;
        }
        if (ColumnProfiler.isNumeric(column.physicalType()) && stats != null && stats.max() != null) {
            // Distinguish epoch seconds (~1e9) from millis (~1e12) by magnitude.
            return stats.max() >= 100_000_000_000d ? TimeEncoding.EPOCH_MILLIS : TimeEncoding.EPOCH_SECONDS;
        }
        return TimeEncoding.TEXT; // text dates -> try_cast (ISO); non-ISO formats are a future refinement
    }

    Optional<TimeColumn> timeColumn(String ref) {
        return timeColumns.stream().filter(t -> t.ref().equals(ref)).findFirst();
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
                    "timeColumns", timeColumns.stream().map(t -> Map.of(
                            "ref", t.ref(), "encoding", t.encoding().name())).toList(),
                    "relationships", relationships));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("failed to serialize SemanticModel", e);
        }
    }
}
