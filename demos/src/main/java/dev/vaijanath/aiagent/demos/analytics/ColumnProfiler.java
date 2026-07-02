package dev.vaijanath.aiagent.demos.analytics;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

final class ColumnProfiler {

    private static final Pattern DATE = Pattern.compile("\\d{4}[/-]\\d{2}[/-]\\d{2}.*");
    private static final Pattern LEADING_ZERO = Pattern.compile("0\\d+");
    private static final Set<String> BOOLS =
            Set.of("true", "false", "yes", "no", "y", "n", "t", "f", "0", "1");

    private ColumnProfiler() {}

    static ColumnProfile profile(CsvSource src, String table, ColumnSchema col) {
        String c = CsvSource.quote(col.name());
        String t = CsvSource.quote(table);
        boolean numeric = isNumeric(col.physicalType());

        Map<String, Object> agg = src.nativeQuery(
                "select count(*) n_rows, count(distinct " + c + ") n_distinct, "
                        + "count(*) filter (where " + c + " is null) n_nulls"
                        + (numeric
                                ? ", min(" + c + ") mn, max(" + c + ") mx, avg(" + c + ") av, stddev_pop(" + c + ") sd"
                                : "")
                        + " from " + t).get(0);

        List<String> samples = new ArrayList<>();
        for (Map<String, Object> r : src.nativeQuery(
                "select distinct " + c + " v from " + t + " where " + c + " is not null limit 20")) {
            samples.add(String.valueOf(r.get("v")));
        }

        long rows = num(agg.get("n_rows"));
        long distinct = num(agg.get("n_distinct"));
        Set<String> signals = new LinkedHashSet<>();
        if (samples.stream().anyMatch(s -> DATE.matcher(s).matches())) {
            signals.add("date-like");
        }
        if (samples.stream().anyMatch(s -> LEADING_ZERO.matcher(s).matches())) {
            signals.add("leading-zeros");
        }
        if (distinct == 2 && samples.stream().allMatch(s -> BOOLS.contains(s.toLowerCase(Locale.ROOT)))) {
            signals.add("bool-like");
        }
        boolean nameId = col.name().toLowerCase(Locale.ROOT).matches(".*(id|key|code|uuid|guid)$");
        // High cardinality means identity only for integer/text columns; a floating-point column
        // that happens to be all-distinct is a continuous measure (revenue, theoWin), not an id.
        boolean highCardKey = !isFloatingPoint(col.physicalType())
                && rows > 0 && (double) distinct / rows > 0.95;
        if (nameId || highCardKey) {
            signals.add("id-like");
        }
        Double min = numeric ? dbl(agg.get("mn")) : null;
        Double max = numeric ? dbl(agg.get("mx")) : null;
        if (numeric && min != null && min >= 1_000_000_000d && max != null && max <= 5_000_000_000_000d) {
            signals.add("epoch-like");
        }
        return new ColumnProfile(
                table, col.name(), col.physicalType(), rows, distinct, num(agg.get("n_nulls")),
                min, max, numeric ? dbl(agg.get("av")) : null, numeric ? dbl(agg.get("sd")) : null,
                samples, signals);
    }

    static boolean isNumeric(String duckType) {
        String s = duckType.toUpperCase(Locale.ROOT);
        return s.contains("INT") || s.contains("DECIMAL") || s.contains("DOUBLE")
                || s.contains("FLOAT") || s.contains("REAL") || s.contains("NUMERIC") || s.contains("HUGEINT");
    }

    static boolean isFloatingPoint(String duckType) {
        String s = duckType.toUpperCase(Locale.ROOT);
        return s.contains("DOUBLE") || s.contains("FLOAT") || s.contains("REAL")
                || s.contains("DECIMAL") || s.contains("NUMERIC");
    }

    private static long num(Object o) {
        return o instanceof Number n ? n.longValue() : 0L;
    }

    private static Double dbl(Object o) {
        return o instanceof Number n ? n.doubleValue() : null;
    }
}
