package dev.vaijanath.aiagent.demos.analytics;

import java.util.List;
import java.util.Map;

interface DataSource extends AutoCloseable {
    List<TableSchema> tables();

    List<Relationship> relationships();

    List<Map<String, Object>> sample(String table, int limit);

    List<Map<String, Object>> nativeQuery(String sql);

    List<Map<String, Object>> nativeQuery(String sql, int maxRows);

    @Override
    void close();
}

enum ColumnRole {
    IDENTIFIER,
    DIMENSION,
    MEASURE_ADDITIVE,
    MEASURE_RATIO,
    TIMESTAMP,
    DATE,
    BOOLEAN,
    TEXT,
    UNKNOWN
}

record ColumnSchema(String name, String physicalType, ColumnRole role) {}

record TableSchema(String name, long rows, List<ColumnSchema> columns) {}

record Relationship(
        String fromTable,
        List<String> fromColumns,
        String toTable,
        List<String> toColumns,
        String cardinality,
        double coverage) {
    String fromColumn() {
        return fromColumns.get(0);
    }

    String toColumn() {
        return toColumns.get(0);
    }
}
