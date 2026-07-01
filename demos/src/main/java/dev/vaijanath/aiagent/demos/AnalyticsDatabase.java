package dev.vaijanath.aiagent.demos;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
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

final class AnalyticsDatabase implements AutoCloseable {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String TABLE = "data";

    private final Connection connection;
    private final List<Column> columns;

    private AnalyticsDatabase(Connection connection, List<Column> columns) {
        this.connection = connection;
        this.columns = List.copyOf(columns);
    }

    static AnalyticsDatabase importCsv(Path database, Path csv) {
        try {
            Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database.toAbsolutePath());
            connection.createStatement().execute("PRAGMA foreign_keys = ON");
            List<List<String>> rows = AnalyticsCsv.read(csv);
            if (rows.size() < 2) {
                throw new IllegalArgumentException("CSV must contain a header and at least one data row");
            }
            List<String> header = rows.getFirst();
            List<List<String>> data = rows.subList(1, rows.size());
            List<Column> columns = inferColumns(header, data);
            createTable(connection, columns);
            insertRows(connection, columns, data);
            return new AnalyticsDatabase(connection, columns);
        } catch (SQLException e) {
            throw new IllegalStateException("failed to import analytics CSV", e);
        }
    }

    int rowCount() {
        return count("select count(*) from " + TABLE);
    }

    String schemaJson() {
        return json(Map.of(
                "table", TABLE,
                "rows", rowCount(),
                "columns", columns.stream()
                        .map(column -> Map.of(
                                "name", column.name(),
                                "sqlName", column.sql().replace("\"", ""),
                                "type", column.numeric() ? "numeric" : "categorical"))
                        .toList()));
    }

    String profileJson() {
        List<Map<String, Object>> profile = new ArrayList<>();
        for (Column column : columns) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("name", column.name());
            entry.put("type", column.numeric() ? "numeric" : "categorical");
            entry.put("missing", scalar("select count(*) from " + TABLE + " where " + column.sql() + " is null"));
            if (column.numeric()) {
                entry.put("min", number("select min(" + column.sql() + ") from " + TABLE));
                entry.put("max", number("select max(" + column.sql() + ") from " + TABLE));
                entry.put("mean", round(number("select avg(" + column.sql() + ") from " + TABLE)));
            } else {
                entry.put("distinct", scalar("select count(distinct " + column.sql() + ") from " + TABLE));
                entry.put("top", topCategory(column));
            }
            profile.add(entry);
        }
        return json(Map.of("table", TABLE, "rows", rowCount(), "columns", profile));
    }

    String columnProfileJson(String columnName) {
        Column column = requireColumn(columnName);
        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("name", column.name());
        profile.put("type", column.numeric() ? "numeric" : "categorical");
        profile.put("missing", scalar("select count(*) from " + TABLE + " where " + column.sql() + " is null"));
        if (column.numeric()) {
            profile.put("min", number("select min(" + column.sql() + ") from " + TABLE));
            profile.put("p25", quantile(column, 0.25));
            profile.put("median", quantile(column, 0.50));
            profile.put("p75", quantile(column, 0.75));
            profile.put("max", number("select max(" + column.sql() + ") from " + TABLE));
            profile.put("mean", round(number("select avg(" + column.sql() + ") from " + TABLE)));
            profile.put("stddev", round(stddev(column)));
        } else {
            profile.put("distinct", scalar("select count(distinct " + column.sql() + ") from " + TABLE));
            profile.put("topValues", topCategories(column, 10));
        }
        return json(profile);
    }

    String dataQualityJson() {
        List<Map<String, Object>> quality = new ArrayList<>();
        for (Column column : columns) {
            long missing = scalar("select count(*) from " + TABLE + " where " + column.sql() + " is null");
            Map<String, Object> issue = new LinkedHashMap<>();
            issue.put("column", column.name());
            issue.put("missing", missing);
            issue.put("missingRate", round((double) missing / Math.max(1, rowCount())));
            if (!column.numeric()) {
                issue.put("distinct", scalar("select count(distinct " + column.sql() + ") from " + TABLE));
            }
            quality.add(issue);
        }
        long duplicateRows = scalar("select count(*) from (select count(*) c from " + TABLE
                + " group by " + columns.stream().map(Column::sql).reduce((a, b) -> a + ", " + b).orElseThrow()
                + " having c > 1)");
        return json(Map.of("rows", rowCount(), "duplicateRowGroups", duplicateRows, "columns", quality));
    }

    String outliersJson(String columnName) {
        Column column = requireNumeric(columnName);
        double q1 = quantile(column, 0.25);
        double q3 = quantile(column, 0.75);
        double iqr = q3 - q1;
        double low = q1 - 1.5 * iqr;
        double high = q3 + 1.5 * iqr;
        return sqlJson("select _row_id, " + column.sql() + " as value from " + TABLE
                + " where " + column.sql() + " < " + low + " or " + column.sql() + " > " + high
                + " order by abs(" + column.sql() + " - " + number("select avg(" + column.sql() + ") from " + TABLE)
                + ") desc");
    }

    String correlationsJson(String target) {
        Column targetColumn = requireNumeric(target);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Column column : columns.stream().filter(Column::numeric).toList()) {
            if (column.name().equals(targetColumn.name())) {
                continue;
            }
            double r = pearson(column, targetColumn);
            rows.add(Map.of("column", column.name(), "pearson", round(r), "abs", round(Math.abs(r))));
        }
        rows.sort(Comparator.comparingDouble(r -> -((Number) r.get("abs")).doubleValue()));
        return json(Map.of("target", targetColumn.name(), "correlations", rows));
    }

    String interestingFeaturesJson(String target) {
        Column targetColumn = requireNumeric(target);
        List<Map<String, Object>> features = new ArrayList<>();
        for (Column column : columns) {
            if (column.name().equals(targetColumn.name())) {
                continue;
            }
            if (column.numeric()) {
                double r = pearson(column, targetColumn);
                features.add(Map.of(
                        "feature", column.name(),
                        "kind", "numeric_correlation",
                        "score", round(Math.abs(r)),
                        "detail", "Pearson r=" + round(r)));
            } else {
                GroupLift lift = categoricalLift(column, targetColumn);
                features.add(Map.of(
                        "feature", column.name(),
                        "kind", "categorical_lift",
                        "score", round(Math.abs(lift.delta())),
                        "detail", "best group '" + lift.bestGroup() + "' avg=" + round(lift.bestMean())
                                + ", worst group '" + lift.worstGroup() + "' avg=" + round(lift.worstMean())));
            }
        }
        features.sort(Comparator.comparingDouble(r -> -((Number) r.get("score")).doubleValue()));
        return json(Map.of("target", targetColumn.name(), "features", features.stream().limit(8).toList()));
    }

    String groupByJson(String dimension, String metric, String aggregation) {
        Column dim = requireColumn(dimension);
        Column value = requireNumeric(metric);
        String fn = aggregationFunction(aggregation);
        return sqlJson("select " + dim.sql() + " as " + quote(dim.name())
                + ", round(" + fn + "(" + value.sql() + "), 3) as " + quote(aggregation + "_" + value.name())
                + ", count(*) as rows from " + TABLE
                + " where " + dim.sql() + " is not null and " + value.sql() + " is not null"
                + " group by " + dim.sql() + " order by 2 desc");
    }

    String pivotJson(String rowDimension, String columnDimension, String metric) {
        Column row = requireColumn(rowDimension);
        Column col = requireColumn(columnDimension);
        Column value = requireNumeric(metric);
        List<String> categories = distinctValues(col, 8);
        String expressions = categories.stream()
                .map(category -> "round(avg(case when " + col.sql() + " = '" + escapeSql(category)
                        + "' then " + value.sql() + " end), 3) as " + quote(category))
                .reduce((a, b) -> a + ", " + b)
                .orElse("count(*) as rows");
        return sqlJson("select " + row.sql() + " as " + quote(row.name()) + ", " + expressions
                + " from " + TABLE + " group by " + row.sql() + " order by " + row.sql());
    }

    String distributionCompareJson(String segment, String target) {
        Column segmentColumn = requireColumn(segment);
        Column targetColumn = requireNumeric(target);
        List<Map<String, Object>> rows = new ArrayList<>();
        try (Statement st = connection.createStatement();
                ResultSet rs = st.executeQuery("select " + segmentColumn.sql()
                        + ", count(*), avg(" + targetColumn.sql() + "), min(" + targetColumn.sql() + "), max("
                        + targetColumn.sql() + ") from " + TABLE + " where " + targetColumn.sql()
                        + " is not null group by " + segmentColumn.sql() + " order by 3 desc")) {
            while (rs.next()) {
                rows.add(Map.of(
                        "segment", rs.getString(1),
                        "rows", rs.getLong(2),
                        "mean", round(rs.getDouble(3)),
                        "min", rs.getDouble(4),
                        "max", rs.getDouble(5)));
            }
            return json(Map.of("segment", segmentColumn.name(), "target", targetColumn.name(), "groups", rows));
        } catch (SQLException e) {
            throw new IllegalStateException("failed to compare distributions", e);
        }
    }

    String hypothesisTestJson(String segment, String target, String groupA, String groupB) {
        Column segmentColumn = requireColumn(segment);
        Column targetColumn = requireNumeric(target);
        NumericSample a = sample("select " + targetColumn.sql() + " from " + TABLE + " where "
                + segmentColumn.sql() + " = '" + escapeSql(groupA) + "' and " + targetColumn.sql() + " is not null");
        NumericSample b = sample("select " + targetColumn.sql() + " from " + TABLE + " where "
                + segmentColumn.sql() + " = '" + escapeSql(groupB) + "' and " + targetColumn.sql() + " is not null");
        double se = Math.sqrt((a.variance() / Math.max(1, a.n())) + (b.variance() / Math.max(1, b.n())));
        double t = se == 0 ? 0 : (a.mean() - b.mean()) / se;
        double effect = pooledStddev(a, b) == 0 ? 0 : (a.mean() - b.mean()) / pooledStddev(a, b);
        return json(Map.of(
                "test", "welch_t_test_approximation",
                "segment", segmentColumn.name(),
                "target", targetColumn.name(),
                "groupA", Map.of("name", groupA, "n", a.n(), "mean", round(a.mean())),
                "groupB", Map.of("name", groupB, "n", b.n(), "mean", round(b.mean())),
                "tStatistic", round(t),
                "effectSizeCohenD", round(effect),
                "note", "Normal approximation only; inspect distributions and sample size before decisions."));
    }

    String regressionJson(String target, List<String> featureNames) {
        Column targetColumn = requireNumeric(target);
        List<Map<String, Object>> models = new ArrayList<>();
        for (String featureName : featureNames) {
            Column feature = requireNumeric(featureName);
            double r = pearson(feature, targetColumn);
            double slope = covariance(feature, targetColumn) / variance(feature);
            double intercept = mean(targetColumn) - slope * mean(feature);
            models.add(Map.of(
                    "feature", feature.name(),
                    "slope", round(slope),
                    "intercept", round(intercept),
                    "r2", round(r * r),
                    "pearson", round(r)));
        }
        models.sort(Comparator.comparingDouble(m -> -((Number) m.get("r2")).doubleValue()));
        return json(Map.of(
                "target", targetColumn.name(),
                "model", "single_feature_linear_regression_screen",
                "features", models,
                "note", "Use as a baseline feature screen, not a production predictive model."));
    }

    String anomalyJson(String columnName) {
        Column column = requireNumeric(columnName);
        double mean = mean(column);
        double stddev = stddev(column);
        if (stddev == 0) {
            return json(Map.of("column", column.name(), "anomalies", List.of()));
        }
        return sqlJson("select _row_id, " + column.sql() + " as value, round((" + column.sql() + " - " + mean
                + ") / " + stddev + ", 3) as z_score from " + TABLE
                + " where abs((" + column.sql() + " - " + mean + ") / " + stddev + ") >= 2"
                + " order by abs(z_score) desc");
    }

    String forecastJson(String timeColumn, String metric, int periods) {
        Column time = requireNumeric(timeColumn);
        Column value = requireNumeric(metric);
        double slope = covariance(time, value) / variance(time);
        double intercept = mean(value) - slope * mean(time);
        double maxTime = number("select max(" + time.sql() + ") from " + TABLE);
        List<Map<String, Object>> forecast = new ArrayList<>();
        for (int i = 1; i <= Math.max(1, Math.min(periods, 12)); i++) {
            double next = maxTime + i;
            forecast.add(Map.of(
                    time.name(), round(next),
                    metric, round(intercept + slope * next),
                    "method", "linear_trend_baseline"));
        }
        return json(Map.of("timeColumn", time.name(), "metric", value.name(), "forecast", forecast));
    }

    String chartSpecJson(String chartType, String x, String y) {
        Column xColumn = requireColumn(x);
        Column yColumn = requireColumn(y);
        Map<String, Object> spec = new LinkedHashMap<>();
        spec.put("$schema", "https://vega.github.io/schema/vega-lite/v5.json");
        spec.put("description", "Generated demo chart for " + yColumn.name() + " by " + xColumn.name());
        spec.put("mark", chartType == null || chartType.isBlank() ? "bar" : chartType);
        spec.put("data", Map.of("values", chartRows(xColumn, yColumn, 25)));
        spec.put("encoding", Map.of(
                "x", Map.of("field", xColumn.name(), "type", xColumn.numeric() ? "quantitative" : "nominal"),
                "y", Map.of("field", yColumn.name(), "type", yColumn.numeric() ? "quantitative" : "nominal")));
        return json(spec);
    }

    Path dashboardHtml(Path output) {
        String channelRevenue = groupByJson("channel", "revenue", "avg");
        String conversionFeatures = interestingFeaturesJson("conversion_rate");
        String causal = causalCheckJson("discount_rate", "revenue");
        String chart = chartSpecJson("bar", "channel", "revenue");
        String html = """
                <!doctype html>
                <html lang="en">
                <head>
                  <meta charset="utf-8">
                  <title>Analytics Insight Dashboard</title>
                  <style>
                    body { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif; margin: 32px; color: #1f2937; }
                    h1 { margin-bottom: 4px; }
                    .grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 16px; }
                    .tile { border: 1px solid #d1d5db; border-radius: 8px; padding: 16px; background: #fff; }
                    pre { white-space: pre-wrap; background: #f9fafb; padding: 12px; border-radius: 6px; overflow: auto; }
                  </style>
                </head>
                <body>
                  <h1>Analytics Insight Dashboard</h1>
                  <p>Generated from the uploaded CSV through SQLite-backed analytics tools.</p>
                  <div class="grid">
                    <section class="tile"><h2>Dataset Profile</h2><pre>%s</pre></section>
                    <section class="tile"><h2>Revenue By Channel</h2><pre>%s</pre></section>
                    <section class="tile"><h2>Conversion Feature Scan</h2><pre>%s</pre></section>
                    <section class="tile"><h2>Causal Caveat</h2><pre>%s</pre></section>
                  </div>
                  <section class="tile"><h2>Chart Spec</h2><pre>%s</pre></section>
                </body>
                </html>
                """.formatted(escapeHtml(profileJson()), escapeHtml(channelRevenue), escapeHtml(conversionFeatures),
                escapeHtml(causal), escapeHtml(chart));
        try {
            Files.writeString(output, html, StandardCharsets.UTF_8);
            return output;
        } catch (IOException e) {
            throw new IllegalStateException("failed to write dashboard", e);
        }
    }

    String causalCheckJson(String treatment, String outcome) {
        Column treatmentColumn = requireColumn(treatment);
        Column outcomeColumn = requireNumeric(outcome);
        GroupLift lift = treatmentColumn.numeric()
                ? numericTreatmentLift(treatmentColumn, outcomeColumn)
                : categoricalLift(treatmentColumn, outcomeColumn);
        return json(Map.of(
                "treatment", treatmentColumn.name(),
                "outcome", outcomeColumn.name(),
                "observationalDelta", round(lift.delta()),
                "higherOutcomeGroup", lift.bestGroup(),
                "lowerOutcomeGroup", lift.worstGroup(),
                "canClaimCausation", false,
                "reason",
                        "This is observational CSV data. The agent can surface associations and candidate "
                                + "drivers, but causal claims need randomization, time ordering, an instrument, "
                                + "or a domain-backed causal design."));
    }

    String sqlJson(String sql) {
        String normalized = sql.strip().toLowerCase(Locale.ROOT);
        if (!(normalized.startsWith("select ") || normalized.startsWith("with "))
                || normalized.contains(";")
                || normalized.contains(" pragma ")
                || normalized.contains(" attach ")) {
            return json(Map.of("error", "Only a single read-only SELECT/CTE statement is allowed"));
        }
        try (PreparedStatement ps = connection.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {
            ResultSetMetaData meta = rs.getMetaData();
            List<Map<String, Object>> rows = new ArrayList<>();
            while (rs.next() && rows.size() < 25) {
                Map<String, Object> row = new LinkedHashMap<>();
                for (int i = 1; i <= meta.getColumnCount(); i++) {
                    Object value = rs.getObject(i);
                    row.put(meta.getColumnLabel(i), value);
                }
                rows.add(row);
            }
            return json(Map.of("rows", rows, "limitedTo", 25));
        } catch (SQLException e) {
            return json(Map.of("error", e.getMessage()));
        }
    }

    @Override
    public void close() {
        try {
            connection.close();
        } catch (SQLException e) {
            throw new IllegalStateException("failed to close analytics database", e);
        }
    }

    private record Column(String name, String sql, boolean numeric) {}

    private record GroupLift(String bestGroup, double bestMean, String worstGroup, double worstMean) {
        double delta() {
            return bestMean - worstMean;
        }
    }

    private record NumericSample(int n, double mean, double variance) {}

    private static List<Column> inferColumns(List<String> header, List<List<String>> rows) {
        List<Column> columns = new ArrayList<>();
        for (int i = 0; i < header.size(); i++) {
            int index = i;
            boolean numeric = rows.stream()
                    .map(row -> index < row.size() ? row.get(index) : "")
                    .filter(value -> !value.isBlank())
                    .allMatch(AnalyticsDatabase::isDouble);
            columns.add(new Column(header.get(i), quote(identifier(header.get(i), i)), numeric));
        }
        return columns;
    }

    private static void createTable(Connection connection, List<Column> columns) throws SQLException {
        try (Statement st = connection.createStatement()) {
            st.executeUpdate("drop table if exists " + TABLE);
            String defs = columns.stream()
                    .map(column -> column.sql() + " " + (column.numeric() ? "real" : "text"))
                    .reduce((a, b) -> a + ", " + b)
                    .orElseThrow();
            st.executeUpdate("create table " + TABLE + " (_row_id integer primary key autoincrement, " + defs + ")");
        }
    }

    private static void insertRows(Connection connection, List<Column> columns, List<List<String>> rows)
            throws SQLException {
        String placeholders = columns.stream().map(c -> "?").reduce((a, b) -> a + ", " + b).orElseThrow();
        String names = columns.stream().map(Column::sql).reduce((a, b) -> a + ", " + b).orElseThrow();
        try (PreparedStatement ps = connection.prepareStatement(
                "insert into " + TABLE + "(" + names + ") values (" + placeholders + ")")) {
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

    private Column requireColumn(String name) {
        return columns.stream()
                .filter(column -> column.name().equalsIgnoreCase(name))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("unknown column: " + name));
    }

    private Column requireNumeric(String name) {
        Column column = requireColumn(name);
        if (!column.numeric()) {
            throw new IllegalArgumentException("column is not numeric: " + name);
        }
        return column;
    }

    private double pearson(Column x, Column y) {
        try (Statement st = connection.createStatement();
                ResultSet rs = st.executeQuery("select " + x.sql() + ", " + y.sql() + " from " + TABLE
                        + " where " + x.sql() + " is not null and " + y.sql() + " is not null")) {
            List<Double> xs = new ArrayList<>();
            List<Double> ys = new ArrayList<>();
            while (rs.next()) {
                xs.add(rs.getDouble(1));
                ys.add(rs.getDouble(2));
            }
            if (xs.size() < 2) {
                return 0.0;
            }
            double meanX = xs.stream().mapToDouble(Double::doubleValue).average().orElse(0);
            double meanY = ys.stream().mapToDouble(Double::doubleValue).average().orElse(0);
            double num = 0;
            double denX = 0;
            double denY = 0;
            for (int i = 0; i < xs.size(); i++) {
                double dx = xs.get(i) - meanX;
                double dy = ys.get(i) - meanY;
                num += dx * dy;
                denX += dx * dx;
                denY += dy * dy;
            }
            return denX == 0 || denY == 0 ? 0.0 : num / Math.sqrt(denX * denY);
        } catch (SQLException e) {
            throw new IllegalStateException("failed to compute correlation", e);
        }
    }

    private GroupLift categoricalLift(Column group, Column target) {
        return lift("select " + group.sql() + ", avg(" + target.sql() + ") from " + TABLE
                + " where " + group.sql() + " is not null and " + target.sql() + " is not null"
                + " group by " + group.sql());
    }

    private GroupLift numericTreatmentLift(Column treatment, Column target) {
        double median = number("select avg(" + treatment.sql() + ") from " + TABLE);
        return lift("select case when " + treatment.sql() + " >= " + median + " then 'high' else 'low' end,"
                + " avg(" + target.sql() + ") from " + TABLE
                + " where " + treatment.sql() + " is not null and " + target.sql() + " is not null group by 1");
    }

    private GroupLift lift(String sql) {
        String best = "";
        String worst = "";
        double bestMean = Double.NEGATIVE_INFINITY;
        double worstMean = Double.POSITIVE_INFINITY;
        try (Statement st = connection.createStatement();
                ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                String group = rs.getString(1);
                double mean = rs.getDouble(2);
                if (mean > bestMean) {
                    best = group;
                    bestMean = mean;
                }
                if (mean < worstMean) {
                    worst = group;
                    worstMean = mean;
                }
            }
            return new GroupLift(best, bestMean, worst, worstMean);
        } catch (SQLException e) {
            throw new IllegalStateException("failed to compute group lift", e);
        }
    }

    private Object topCategory(Column column) {
        try (Statement st = connection.createStatement();
                ResultSet rs = st.executeQuery("select " + column.sql() + ", count(*) from " + TABLE
                        + " where " + column.sql() + " is not null group by " + column.sql()
                        + " order by 2 desc limit 1")) {
            if (!rs.next()) {
                return null;
            }
            return Map.of("value", rs.getString(1), "count", rs.getInt(2));
        } catch (SQLException e) {
            throw new IllegalStateException("failed to compute top category", e);
        }
    }

    private List<Map<String, Object>> topCategories(Column column, int limit) {
        List<Map<String, Object>> rows = new ArrayList<>();
        try (Statement st = connection.createStatement();
                ResultSet rs = st.executeQuery("select " + column.sql() + ", count(*) from " + TABLE
                        + " where " + column.sql() + " is not null group by " + column.sql()
                        + " order by 2 desc limit " + Math.max(1, limit))) {
            while (rs.next()) {
                rows.add(Map.of("value", rs.getString(1), "count", rs.getLong(2)));
            }
            return rows;
        } catch (SQLException e) {
            throw new IllegalStateException("failed to compute top categories", e);
        }
    }

    private List<String> distinctValues(Column column, int limit) {
        List<String> values = new ArrayList<>();
        try (Statement st = connection.createStatement();
                ResultSet rs = st.executeQuery("select distinct " + column.sql() + " from " + TABLE
                        + " where " + column.sql() + " is not null order by " + column.sql()
                        + " limit " + Math.max(1, limit))) {
            while (rs.next()) {
                values.add(rs.getString(1));
            }
            return values;
        } catch (SQLException e) {
            throw new IllegalStateException("failed to fetch distinct values", e);
        }
    }

    private List<Map<String, Object>> chartRows(Column x, Column y, int limit) {
        List<Map<String, Object>> rows = new ArrayList<>();
        String sql = x.numeric()
                ? "select " + x.sql() + ", " + y.sql() + " from " + TABLE
                        + " where " + x.sql() + " is not null and " + y.sql() + " is not null"
                        + " order by " + x.sql() + " limit " + Math.max(1, limit)
                : "select " + x.sql() + ", avg(" + y.sql() + ") from " + TABLE
                        + " where " + x.sql() + " is not null and " + y.sql() + " is not null"
                        + " group by " + x.sql() + " order by 2 desc limit " + Math.max(1, limit);
        try (Statement st = connection.createStatement();
                ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                rows.add(Map.of(x.name(), rs.getObject(1), y.name(), round(rs.getDouble(2))));
            }
            return rows;
        } catch (SQLException e) {
            throw new IllegalStateException("failed to produce chart rows", e);
        }
    }

    private double quantile(Column column, double q) {
        List<Double> values = numericValues(column);
        if (values.isEmpty()) {
            return 0;
        }
        int index = (int) Math.floor(Math.max(0, Math.min(1, q)) * (values.size() - 1));
        return round(values.get(index));
    }

    private double mean(Column column) {
        return number("select avg(" + column.sql() + ") from " + TABLE + " where " + column.sql() + " is not null");
    }

    private double variance(Column column) {
        List<Double> values = numericValues(column);
        if (values.size() < 2) {
            return 0;
        }
        double mean = values.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        return values.stream().mapToDouble(v -> Math.pow(v - mean, 2)).sum() / (values.size() - 1);
    }

    private double stddev(Column column) {
        return Math.sqrt(variance(column));
    }

    private double covariance(Column x, Column y) {
        try (Statement st = connection.createStatement();
                ResultSet rs = st.executeQuery("select " + x.sql() + ", " + y.sql() + " from " + TABLE
                        + " where " + x.sql() + " is not null and " + y.sql() + " is not null")) {
            List<Double> xs = new ArrayList<>();
            List<Double> ys = new ArrayList<>();
            while (rs.next()) {
                xs.add(rs.getDouble(1));
                ys.add(rs.getDouble(2));
            }
            if (xs.size() < 2) {
                return 0;
            }
            double meanX = xs.stream().mapToDouble(Double::doubleValue).average().orElse(0);
            double meanY = ys.stream().mapToDouble(Double::doubleValue).average().orElse(0);
            double sum = 0;
            for (int i = 0; i < xs.size(); i++) {
                sum += (xs.get(i) - meanX) * (ys.get(i) - meanY);
            }
            return sum / (xs.size() - 1);
        } catch (SQLException e) {
            throw new IllegalStateException("failed to compute covariance", e);
        }
    }

    private List<Double> numericValues(Column column) {
        List<Double> values = new ArrayList<>();
        try (Statement st = connection.createStatement();
                ResultSet rs = st.executeQuery("select " + column.sql() + " from " + TABLE
                        + " where " + column.sql() + " is not null order by " + column.sql())) {
            while (rs.next()) {
                values.add(rs.getDouble(1));
            }
            return values;
        } catch (SQLException e) {
            throw new IllegalStateException("failed to fetch numeric values", e);
        }
    }

    private NumericSample sample(String sql) {
        List<Double> values = new ArrayList<>();
        try (Statement st = connection.createStatement();
                ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                values.add(rs.getDouble(1));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("failed to load sample", e);
        }
        if (values.isEmpty()) {
            return new NumericSample(0, 0, 0);
        }
        double mean = values.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double variance = values.size() < 2
                ? 0
                : values.stream().mapToDouble(v -> Math.pow(v - mean, 2)).sum() / (values.size() - 1);
        return new NumericSample(values.size(), mean, variance);
    }

    private static double pooledStddev(NumericSample a, NumericSample b) {
        int df = a.n() + b.n() - 2;
        if (df <= 0) {
            return 0;
        }
        return Math.sqrt(((a.n() - 1) * a.variance() + (b.n() - 1) * b.variance()) / df);
    }

    private static String aggregationFunction(String aggregation) {
        return switch ((aggregation == null ? "avg" : aggregation).toLowerCase(Locale.ROOT)) {
            case "sum" -> "sum";
            case "min" -> "min";
            case "max" -> "max";
            case "count" -> "count";
            default -> "avg";
        };
    }

    private static String escapeSql(String value) {
        return value.replace("'", "''");
    }

    private static String escapeHtml(String value) {
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private long scalar(String sql) {
        try (Statement st = connection.createStatement();
                ResultSet rs = st.executeQuery(sql)) {
            return rs.next() ? rs.getLong(1) : 0;
        } catch (SQLException e) {
            throw new IllegalStateException("database scalar query failed", e);
        }
    }

    private double number(String sql) {
        try (Statement st = connection.createStatement();
                ResultSet rs = st.executeQuery(sql)) {
            return rs.next() ? rs.getDouble(1) : 0;
        } catch (SQLException e) {
            throw new IllegalStateException("database number query failed", e);
        }
    }

    private int count(String sql) {
        return (int) scalar(sql);
    }

    private static String json(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("failed to serialize analytics result", e);
        }
    }

    private static double round(double value) {
        return Math.round(value * 1_000.0) / 1_000.0;
    }

    private static boolean isDouble(String value) {
        try {
            Double.parseDouble(value);
            return true;
        } catch (NumberFormatException e) {
            return false;
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
}
