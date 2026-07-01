package dev.vaijanath.aiagent.demos;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import tech.tablesaw.aggregate.AggregateFunction;
import tech.tablesaw.aggregate.AggregateFunctions;
import tech.tablesaw.api.NumericColumn;
import tech.tablesaw.api.Row;
import tech.tablesaw.api.Table;
import tech.tablesaw.columns.Column;

final class AnalyticsFrame {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Table table;

    private AnalyticsFrame(Table table) {
        this.table = table;
    }

    static AnalyticsFrame loadCsv(Path csv) {
        try {
            return new AnalyticsFrame(Table.read().csv(csv.toString()));
        } catch (Exception e) {
            throw new IllegalStateException("failed to load CSV with Tablesaw", e);
        }
    }

    int rowCount() {
        return table.rowCount();
    }

    int columnCount() {
        return table.columnCount();
    }

    String profileJson() {
        List<Map<String, Object>> columns = new ArrayList<>();
        for (Column<?> column : table.columns()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("name", column.name());
            entry.put("type", column.type().name());
            entry.put("missing", column.countMissing());
            entry.put("unique", column.countUnique());
            if (column instanceof NumericColumn<?> numeric) {
                entry.put("min", round(numeric.min()));
                entry.put("p25", round(numeric.quartile1()));
                entry.put("median", round(numeric.median()));
                entry.put("p75", round(numeric.quartile3()));
                entry.put("max", round(numeric.max()));
                entry.put("mean", round(numeric.mean()));
                entry.put("stddev", round(numeric.standardDeviation()));
            } else {
                entry.put("topValues", topValues(column, 5));
            }
            columns.add(entry);
        }
        return json(Map.of(
                "engine", "tablesaw",
                "name", table.name(),
                "rows", table.rowCount(),
                "columns", table.columnCount(),
                "columnNames", table.columnNames(),
                "columnProfile", columns));
    }

    String correlationsJson(String target) {
        NumericColumn<?> targetColumn = table.numberColumn(target);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (NumericColumn<?> column : table.numericColumns()) {
            if (!column.name().equals(targetColumn.name())) {
                double pearson = column.pearsons(targetColumn);
                double spearman = column.spearmans(targetColumn);
                rows.add(Map.of(
                        "column", column.name(),
                        "pearson", round(pearson),
                        "spearman", round(spearman),
                        "absPearson", round(Math.abs(pearson))));
            }
        }
        rows.sort(Comparator.comparingDouble(row -> -((Number) row.get("absPearson")).doubleValue()));
        return json(Map.of("engine", "tablesaw", "target", targetColumn.name(), "correlations", rows));
    }

    String groupByJson(String dimension, String metric, String aggregation) {
        Table summary = table.summarize(metric, aggregate(aggregation)).by(dimension);
        return tableJson("tablesaw", summary, 50);
    }

    String chartSpecJson(String chartType, String x, String y) {
        String mark = chartType == null || chartType.isBlank() ? "bar" : chartType;
        Table chartData = table.column(x) instanceof NumericColumn<?>
                ? table.selectColumns(x, y).sortOn(x).first(Math.min(table.rowCount(), 50))
                : table.summarize(y, AggregateFunctions.mean).by(x);
        Map<String, Object> spec = new LinkedHashMap<>();
        spec.put("$schema", "https://vega.github.io/schema/vega-lite/v5.json");
        spec.put("description", "Tablesaw-prepared chart for " + y + " by " + x);
        spec.put("mark", mark);
        spec.put("data", Map.of("values", tableRows(chartData, 50)));
        spec.put("encoding", Map.of(
                "x", Map.of("field", x, "type", table.column(x) instanceof NumericColumn<?> ? "quantitative" : "nominal"),
                "y", Map.of("field", y, "type", "quantitative")));
        return json(spec);
    }

    private static List<Map<String, Object>> topValues(Column<?> column, int limit) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (int i = 0; i < column.size(); i++) {
            if (!column.isMissing(i)) {
                counts.merge(column.getString(i), 1L, Long::sum);
            }
        }
        return counts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue(Comparator.reverseOrder()))
                .limit(limit)
                .map(entry -> Map.<String, Object>of("value", entry.getKey(), "count", entry.getValue()))
                .collect(Collectors.toList());
    }

    private static AggregateFunction<?, ?> aggregate(String aggregation) {
        return switch ((aggregation == null ? "avg" : aggregation).toLowerCase()) {
            case "sum" -> AggregateFunctions.sum;
            case "min" -> AggregateFunctions.min;
            case "max" -> AggregateFunctions.max;
            case "count" -> AggregateFunctions.count;
            default -> AggregateFunctions.mean;
        };
    }

    private static String tableJson(String engine, Table table, int limit) {
        return json(Map.of(
                "engine", engine,
                "columns", table.columnNames(),
                "rows", tableRows(table, limit),
                "limitedTo", limit));
    }

    private static List<Map<String, Object>> tableRows(Table table, int limit) {
        List<Map<String, Object>> rows = new ArrayList<>();
        int max = Math.min(table.rowCount(), Math.max(1, limit));
        for (int i = 0; i < max; i++) {
            Row row = table.row(i);
            Map<String, Object> values = new LinkedHashMap<>();
            for (int c = 0; c < table.columnCount(); c++) {
                Object value = row.getObject(c);
                values.put(table.column(c).name(), value instanceof Number number ? round(number.doubleValue()) : value);
            }
            rows.add(values);
        }
        return rows;
    }

    private static double round(double value) {
        if (!Double.isFinite(value)) {
            return 0;
        }
        return Math.round(value * 1_000.0) / 1_000.0;
    }

    private static String json(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("failed to serialize dataframe profile", e);
        }
    }
}
