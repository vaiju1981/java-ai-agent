package dev.vaijanath.aiagent.demos;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class AnalyticsMultiTableDatabase implements AutoCloseable {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Connection connection;
    private final List<TableModel> tables;

    private AnalyticsMultiTableDatabase(Connection connection, List<TableModel> tables) {
        this.connection = connection;
        this.tables = List.copyOf(tables);
    }

    static AnalyticsMultiTableDatabase importCsvs(Path database, Map<String, Path> csvs) {
        if (csvs.size() < 2) {
            throw new IllegalArgumentException("multi-table import requires at least two CSVs");
        }
        try {
            Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database.toAbsolutePath());
            connection.createStatement().execute("PRAGMA foreign_keys = ON");
            List<TableModel> tables = new ArrayList<>();
            int index = 0;
            for (Map.Entry<String, Path> entry : csvs.entrySet()) {
                tables.add(importTable(connection, entry.getKey(), entry.getValue(), index++));
            }
            return new AnalyticsMultiTableDatabase(connection, tables);
        } catch (SQLException e) {
            throw new IllegalStateException("failed to import multi-table analytics CSVs", e);
        }
    }

    String schemaJson() {
        return json(Map.of(
                "tables",
                tables.stream()
                        .map(table -> Map.of(
                                "name", table.name(),
                                "rows", table.rows(),
                                "source", table.source().toAbsolutePath().toString(),
                                "columns", table.columns().stream()
                                        .map(column -> Map.of(
                                                "name", column.name(),
                                                "type", column.numeric() ? "numeric" : "categorical",
                                                "distinct", distinctCount(table, column),
                                                "unique", distinctCount(table, column) == table.rows()))
                                        .toList()))
                        .toList()));
    }

    String relationshipsJson() {
        return json(Map.of("relationships", discoverRelationships()));
    }

    String semanticCatalogJson() {
        return json(Map.of(
                "metrics", semanticMetrics().stream().map(SemanticMetric::asMap).toList(),
                "dimensions", semanticDimensions().stream().map(SemanticDimension::asMap).toList(),
                "relationships", discoverRelationships()));
    }

    String semanticPlanJson(String question) {
        return json(semanticPlan(question).asMap());
    }

    String semanticQueryJson(String question) {
        SemanticPlan plan = semanticPlan(question);
        return json(Map.of("plan", plan.asMap(), "result", parse(sqlJson(plan.sql()))));
    }

    String revenueByCustomerSegmentJson() {
        Relationship relationship = discoverRelationships().stream()
                .filter(candidate -> candidate.fromTable().equals("orders") && candidate.toTable().equals("customers"))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("orders -> customers relationship was not discovered"));
        return sqlJson("select c.segment, c.region, round(sum(o.revenue), 2) as total_revenue, "
                + "round(avg(o.conversion_rate), 3) as avg_conversion_rate, count(*) as orders "
                + "from " + quote(relationship.fromTable()) + " o "
                + "join " + quote(relationship.toTable()) + " c "
                + "on o." + quote(relationship.fromColumn()) + " = c." + quote(relationship.toColumn()) + " "
                + "group by c.segment, c.region order by total_revenue desc");
    }

    private SemanticPlan semanticPlan(String question) {
        Relationship relationship = ordersCustomersRelationship();
        String q = question.toLowerCase(Locale.ROOT);
        List<SemanticMetric> selectedMetrics = new ArrayList<>();
        if (q.contains("revenue")) {
            selectedMetrics.add(metric("total_revenue"));
        }
        if (q.contains("conversion")) {
            selectedMetrics.add(metric("avg_conversion_rate"));
        }
        if (q.contains("order")) {
            selectedMetrics.add(metric("order_count"));
        }
        if (q.contains("customer")) {
            selectedMetrics.add(metric("customer_count"));
        }
        if (selectedMetrics.isEmpty()) {
            selectedMetrics.add(metric("total_revenue"));
        }

        List<SemanticDimension> selectedDimensions = new ArrayList<>();
        if (q.contains("segment")) {
            selectedDimensions.add(dimension("customer_segment"));
        }
        if (q.contains("region")) {
            selectedDimensions.add(dimension("region"));
        }
        if (q.contains("channel")) {
            selectedDimensions.add(dimension("channel"));
        }
        if (q.contains("week")) {
            selectedDimensions.add(dimension("week"));
        }
        if (selectedDimensions.isEmpty()) {
            selectedDimensions.add(dimension("customer_segment"));
        }

        String sql = semanticSql(relationship, selectedMetrics, selectedDimensions);
        return new SemanticPlan(
                question,
                selectedMetrics.stream().map(SemanticMetric::name).toList(),
                selectedDimensions.stream().map(SemanticDimension::name).toList(),
                relationship.fromTable() + "." + relationship.fromColumn() + " -> "
                        + relationship.toTable() + "." + relationship.toColumn(),
                sql,
                "Mapped metrics and dimensions across the discovered orders/customers relationship.");
    }

    private String semanticSql(
            Relationship relationship, List<SemanticMetric> metrics, List<SemanticDimension> dimensions) {
        List<String> select = new ArrayList<>();
        for (SemanticDimension dimension : dimensions) {
            select.add(dimension.expression() + " as " + quote(dimension.name()));
        }
        for (SemanticMetric metric : metrics) {
            select.add(metric.expression() + " as " + quote(metric.name()));
        }
        List<String> groupBy = dimensions.stream().map(SemanticDimension::expression).toList();
        return "select " + String.join(", ", select)
                + " from " + quote(relationship.fromTable()) + " o"
                + " join " + quote(relationship.toTable()) + " c"
                + " on o." + quote(relationship.fromColumn()) + " = c." + quote(relationship.toColumn())
                + " group by " + String.join(", ", groupBy)
                + " order by " + quote(metrics.getFirst().name()) + " desc";
    }

    private Relationship ordersCustomersRelationship() {
        return discoverRelationships().stream()
                .filter(candidate -> candidate.fromTable().equals("orders") && candidate.toTable().equals("customers"))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("orders -> customers relationship was not discovered"));
    }

    private SemanticMetric metric(String name) {
        return semanticMetrics().stream()
                .filter(metric -> metric.name().equals(name))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("unknown semantic metric: " + name));
    }

    private SemanticDimension dimension(String name) {
        return semanticDimensions().stream()
                .filter(dimension -> dimension.name().equals(name))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("unknown semantic dimension: " + name));
    }

    private static List<SemanticMetric> semanticMetrics() {
        return List.of(
                new SemanticMetric(
                        "total_revenue",
                        "round(sum(o.revenue), 2)",
                        "Total order revenue joined to customer dimensions.",
                        "money"),
                new SemanticMetric(
                        "avg_conversion_rate",
                        "round(avg(o.conversion_rate), 3)",
                        "Average order conversion rate.",
                        "rate"),
                new SemanticMetric("order_count", "count(*)", "Number of joined order rows.", "count"),
                new SemanticMetric(
                        "customer_count",
                        "count(distinct c.customer_id)",
                        "Distinct customers represented in the joined result.",
                        "count"));
    }

    private static List<SemanticDimension> semanticDimensions() {
        return List.of(
                new SemanticDimension("customer_segment", "c.segment", "Customer market segment."),
                new SemanticDimension("region", "c.region", "Customer region."),
                new SemanticDimension("channel", "o.channel", "Order acquisition channel."),
                new SemanticDimension("week", "o.week", "Order week."));
    }

    @Override
    public void close() {
        try {
            connection.close();
        } catch (SQLException e) {
            throw new IllegalStateException("failed to close multi-table analytics database", e);
        }
    }

    private List<Relationship> discoverRelationships() {
        List<Relationship> relationships = new ArrayList<>();
        for (TableModel left : tables) {
            for (TableModel right : tables) {
                if (left.name().equals(right.name())) {
                    continue;
                }
                for (ColumnModel leftColumn : left.columns()) {
                    for (ColumnModel rightColumn : right.columns()) {
                        if (!joinCandidate(left, leftColumn, right, rightColumn)) {
                            continue;
                        }
                        Relationship relationship = relationship(left, leftColumn, right, rightColumn);
                        if (relationship != null) {
                            relationships.add(relationship);
                        }
                    }
                }
            }
        }
        return relationships.stream()
                .distinct()
                .sorted(Comparator.comparing(Relationship::score).reversed())
                .toList();
    }

    private Relationship relationship(TableModel left, ColumnModel leftColumn, TableModel right, ColumnModel rightColumn) {
        long leftDistinct = distinctCount(left, leftColumn);
        long rightDistinct = distinctCount(right, rightColumn);
        if (leftDistinct == 0 || rightDistinct == 0) {
            return null;
        }
        long overlap = overlap(left, leftColumn, right, rightColumn);
        double leftCoverage = (double) overlap / leftDistinct;
        double rightCoverage = (double) overlap / rightDistinct;
        if (Math.max(leftCoverage, rightCoverage) < 0.8) {
            return null;
        }
        boolean leftUnique = leftDistinct == left.rows();
        boolean rightUnique = rightDistinct == right.rows();
        if (rightUnique && !leftUnique) {
            return new Relationship(
                    left.name(), leftColumn.name(), right.name(), rightColumn.name(),
                    "many_to_one", round(leftCoverage), overlap);
        }
        if (leftUnique && !rightUnique) {
            return new Relationship(
                    right.name(), rightColumn.name(), left.name(), leftColumn.name(),
                    "many_to_one", round(rightCoverage), overlap);
        }
        return null;
    }

    private static boolean joinCandidate(
            TableModel left, ColumnModel leftColumn, TableModel right, ColumnModel rightColumn) {
        String leftName = leftColumn.name();
        String rightName = rightColumn.name();
        return leftName.equals(rightName)
                || leftName.equals(right.name() + "_id")
                || rightName.equals(left.name() + "_id")
                || (leftName.endsWith("_id") && rightName.endsWith("_id"));
    }

    private long distinctCount(TableModel table, ColumnModel column) {
        return scalar("select count(distinct " + quote(column.name()) + ") from " + quote(table.name())
                + " where " + quote(column.name()) + " is not null");
    }

    private long overlap(TableModel left, ColumnModel leftColumn, TableModel right, ColumnModel rightColumn) {
        return scalar("select count(*) from (select distinct " + quote(leftColumn.name()) + " as value from "
                + quote(left.name()) + " where " + quote(leftColumn.name()) + " is not null "
                + "intersect select distinct " + quote(rightColumn.name()) + " as value from " + quote(right.name())
                + " where " + quote(rightColumn.name()) + " is not null)");
    }

    private static TableModel importTable(Connection connection, String requestedName, Path csv, int tableIndex)
            throws SQLException {
        List<List<String>> rows = AnalyticsCsv.read(csv);
        if (rows.size() < 2) {
            throw new IllegalArgumentException("CSV must contain a header and at least one data row: " + csv);
        }
        List<String> header = rows.getFirst();
        List<List<String>> data = rows.subList(1, rows.size());
        String tableName = identifier(requestedName, tableIndex);
        List<ColumnModel> columns = inferColumns(header, data);
        createTable(connection, tableName, columns);
        insertRows(connection, tableName, columns, data);
        return new TableModel(tableName, csv, data.size(), columns);
    }

    private static List<ColumnModel> inferColumns(List<String> header, List<List<String>> rows) {
        List<ColumnModel> columns = new ArrayList<>();
        for (int i = 0; i < header.size(); i++) {
            int index = i;
            boolean numeric = rows.stream()
                    .map(row -> index < row.size() ? row.get(index) : "")
                    .filter(value -> !value.isBlank())
                    .allMatch(AnalyticsMultiTableDatabase::isDouble);
            columns.add(new ColumnModel(identifier(header.get(i), i), numeric));
        }
        return columns;
    }

    private static void createTable(Connection connection, String tableName, List<ColumnModel> columns)
            throws SQLException {
        try (Statement st = connection.createStatement()) {
            st.executeUpdate("drop table if exists " + quote(tableName));
            String defs = columns.stream()
                    .map(column -> quote(column.name()) + " " + (column.numeric() ? "real" : "text"))
                    .reduce((a, b) -> a + ", " + b)
                    .orElseThrow();
            st.executeUpdate("create table " + quote(tableName) + " (" + defs + ")");
        }
    }

    private static void insertRows(
            Connection connection, String tableName, List<ColumnModel> columns, List<List<String>> rows)
            throws SQLException {
        String placeholders = columns.stream().map(c -> "?").reduce((a, b) -> a + ", " + b).orElseThrow();
        String names = columns.stream()
                .map(column -> quote(column.name()))
                .reduce((a, b) -> a + ", " + b)
                .orElseThrow();
        try (PreparedStatement ps = connection.prepareStatement(
                "insert into " + quote(tableName) + "(" + names + ") values (" + placeholders + ")")) {
            for (List<String> row : rows) {
                for (int i = 0; i < columns.size(); i++) {
                    String value = i < row.size() ? row.get(i) : "";
                    if (value.isBlank()) {
                        ps.setObject(i + 1, null);
                    } else if (columns.get(i).numeric()) {
                        ps.setDouble(i + 1, Double.parseDouble(value));
                    } else {
                        ps.setString(i + 1, value);
                    }
                }
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private String sqlJson(String sql) {
        try (PreparedStatement ps = connection.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {
            ResultSetMetaData meta = rs.getMetaData();
            List<Map<String, Object>> rows = new ArrayList<>();
            while (rs.next() && rows.size() < 25) {
                Map<String, Object> row = new LinkedHashMap<>();
                for (int i = 1; i <= meta.getColumnCount(); i++) {
                    row.put(meta.getColumnLabel(i), rs.getObject(i));
                }
                rows.add(row);
            }
            return json(Map.of("rows", rows, "limitedTo", 25));
        } catch (SQLException e) {
            return json(Map.of("error", e.getMessage()));
        }
    }

    private long scalar(String sql) {
        try (Statement st = connection.createStatement();
                ResultSet rs = st.executeQuery(sql)) {
            return rs.next() ? rs.getLong(1) : 0;
        } catch (SQLException e) {
            throw new IllegalStateException("multi-table scalar query failed", e);
        }
    }

    private record TableModel(String name, Path source, int rows, List<ColumnModel> columns) {}

    private record ColumnModel(String name, boolean numeric) {}

    private record Relationship(
            String fromTable,
            String fromColumn,
            String toTable,
            String toColumn,
            String cardinality,
            double score,
            long overlap) {}

    private record SemanticMetric(String name, String expression, String description, String unit) {
        Map<String, Object> asMap() {
            return Map.of(
                    "name", name,
                    "expression", expression,
                    "description", description,
                    "unit", unit);
        }
    }

    private record SemanticDimension(String name, String expression, String description) {
        Map<String, Object> asMap() {
            return Map.of(
                    "name", name,
                    "expression", expression,
                    "description", description);
        }
    }

    private record SemanticPlan(
            String question,
            List<String> metrics,
            List<String> dimensions,
            String relationship,
            String sql,
            String explanation) {
        Map<String, Object> asMap() {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("question", question);
            value.put("metrics", metrics);
            value.put("dimensions", dimensions);
            value.put("relationship", relationship);
            value.put("sql", sql);
            value.put("explanation", explanation);
            return value;
        }
    }

    private static String identifier(String value, int index) {
        String id = value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_");
        id = id.replaceAll("^_+|_+$", "");
        if (id.isBlank() || Character.isDigit(id.charAt(0))) {
            id = "column_" + (index + 1);
        }
        return id;
    }

    private static String quote(String id) {
        return "\"" + id.replace("\"", "\"\"") + "\"";
    }

    private static boolean isDouble(String value) {
        try {
            Double.parseDouble(value);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static double round(double value) {
        return Math.round(value * 1_000.0) / 1_000.0;
    }

    private static String json(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("failed to serialize multi-table analytics result", e);
        }
    }

    private static Object parse(String json) {
        try {
            return MAPPER.readValue(json, Object.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("failed to parse multi-table analytics result", e);
        }
    }
}
