package dev.vaijanath.aiagent.demos.analytics;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Turns a metric/dimension request into read-only SQL over the {@link SemanticModel}. Single-table
 * requests group by directly; two-table requests join on the discovered relationship. Joins across
 * two fact tables (shared keys) are aggregated to grain first to stay fan-out-safe (see M2 Task 3).
 */
final class QueryPlanner {

    private QueryPlanner() {}

    record QuerySpec(List<String> metrics, List<String> dimensions) {}

    static String plan(SemanticModel model, QuerySpec spec) {
        List<SemanticModel.Metric> ms = resolveMetrics(model, spec.metrics());
        List<SemanticModel.Dimension> ds = resolveDimensions(model, spec.dimensions());
        if (ms.isEmpty()) {
            throw new IllegalArgumentException("at least one metric is required");
        }
        Set<String> tables = new LinkedHashSet<>();
        ms.forEach(m -> tables.add(m.table()));
        ds.forEach(d -> tables.add(d.table()));
        if (tables.size() == 1) {
            return singleTable(tables.iterator().next(), ms, ds);
        }
        if (tables.size() == 2) {
            return twoTable(model, tables, ms, ds);
        }
        throw new UnsupportedOperationException("queries spanning more than two tables are not planned yet");
    }

    private static String singleTable(
            String table, List<SemanticModel.Metric> ms, List<SemanticModel.Dimension> ds) {
        return groupByQuery(q(table), ms, ds);
    }

    private static String twoTable(SemanticModel model, Set<String> tables,
            List<SemanticModel.Metric> ms, List<SemanticModel.Dimension> ds) {
        Iterator<String> it = tables.iterator();
        String a = it.next();
        String b = it.next();
        Relationship rel = relationshipBetween(model, a, b);
        if (rel == null) {
            throw new IllegalArgumentException("no discovered relationship between " + a + " and " + b);
        }
        if (!"many_to_one".equals(rel.cardinality()) && !"one_to_one".equals(rel.cardinality())) {
            throw new UnsupportedOperationException(
                    "fan-out-safe join for cardinality '" + rel.cardinality() + "' not implemented yet");
        }
        String from = q(rel.fromTable()) + " join " + q(rel.toTable())
                + " on " + qcol(rel.fromTable(), rel.fromColumn()) + " = " + qcol(rel.toTable(), rel.toColumn());
        return groupByQuery(from, ms, ds);
    }

    private static String groupByQuery(
            String from, List<SemanticModel.Metric> ms, List<SemanticModel.Dimension> ds) {
        List<String> select = new ArrayList<>();
        for (SemanticModel.Dimension d : ds) {
            select.add(qcol(d.table(), d.column()) + " as " + q(d.column()));
        }
        for (SemanticModel.Metric m : ms) {
            select.add(m.aggregation() + "(" + qcol(m.table(), m.column()) + ") as " + q(m.column()));
        }
        StringBuilder sql = new StringBuilder("select " + String.join(", ", select) + " from " + from);
        if (!ds.isEmpty()) {
            List<String> group = ds.stream().map(d -> qcol(d.table(), d.column())).toList();
            sql.append(" group by ").append(String.join(", ", group));
        }
        return sql.toString();
    }

    private static Relationship relationshipBetween(SemanticModel model, String a, String b) {
        return model.relationships().stream()
                .filter(r -> (r.fromTable().equals(a) && r.toTable().equals(b))
                        || (r.fromTable().equals(b) && r.toTable().equals(a)))
                .findFirst()
                .orElse(null);
    }

    private static List<SemanticModel.Metric> resolveMetrics(SemanticModel model, List<String> refs) {
        List<SemanticModel.Metric> out = new ArrayList<>();
        for (String ref : refs) {
            out.add(model.metrics().stream().filter(m -> m.ref().equals(ref)).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("unknown metric: " + ref)));
        }
        return out;
    }

    private static List<SemanticModel.Dimension> resolveDimensions(SemanticModel model, List<String> refs) {
        List<SemanticModel.Dimension> out = new ArrayList<>();
        for (String ref : refs) {
            out.add(model.dimensions().stream().filter(d -> d.ref().equals(ref)).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("unknown dimension: " + ref)));
        }
        return out;
    }

    private static String q(String id) {
        return CsvSource.quote(id);
    }

    private static String qcol(String table, String column) {
        return CsvSource.quote(table) + "." + CsvSource.quote(column);
    }
}
