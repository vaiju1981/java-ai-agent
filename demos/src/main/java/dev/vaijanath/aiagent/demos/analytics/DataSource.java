package dev.vaijanath.aiagent.demos.analytics;

import java.util.List;
import java.util.Map;

interface DataSource extends AutoCloseable {
    List<TableSchema> tables();

    List<Relationship> relationships();

    List<Map<String, Object>> sample(String table, int limit);

    List<Map<String, Object>> nativeQuery(String sql);

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
        String fromColumn,
        String toTable,
        String toColumn,
        String cardinality,
        double coverage) {}
