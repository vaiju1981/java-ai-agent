package dev.vaijanath.aiagent.demos.analytics;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Turns a metric/dimension/filter request into read-only SQL over the {@link SemanticModel}.
 * Single-table requests group by directly; two-table requests join on the discovered relationship
 * (shared-key two-fact joins pre-aggregate to grain to stay fan-out-safe). Filters and a
 * DB-computed "last N days" window are applied as a WHERE clause.
 */
final class QueryPlanner {

    private static final Set<String> OPERATORS = Set.of("=", "!=", "<>", "<", "<=", ">", ">=");

    private QueryPlanner() {}

    record Filter(String column, String op, String value) {}

    record QuerySpec(
            List<String> metrics, List<String> dimensions, List<Filter> filters,
            Integer lastDays, String timeColumn,
            String orderBy, boolean descending, Integer limit) {
        QuerySpec {
            metrics = metrics == null ? List.of() : List.copyOf(metrics);
            dimensions = dimensions == null ? List.of() : List.copyOf(dimensions);
            filters = filters == null ? List.of() : List.copyOf(filters);
        }

        QuerySpec(List<String> metrics, List<String> dimensions, List<Filter> filters,
                Integer lastDays, String timeColumn) {
            this(metrics, dimensions, filters, lastDays, timeColumn, null, true, null);
        }

        QuerySpec(List<String> metrics, List<String> dimensions) {
            this(metrics, dimensions, List.of(), null, null);
        }
    }

    static String plan(SemanticModel model, QuerySpec spec) {
        List<SemanticModel.Metric> ms = resolveMetrics(model, spec.metrics());
        List<SemanticModel.Dimension> ds = resolveDimensions(model, spec.dimensions());
        if (ms.isEmpty()) {
            throw new IllegalArgumentException("at least one metric is required");
        }
        String where = buildWhere(model, spec);
        Set<String> tables = new LinkedHashSet<>();
        ms.forEach(m -> tables.add(m.table()));
        ds.forEach(d -> tables.add(d.table()));
        String sql;
        if (tables.size() == 1) {
            sql = groupByQuery(q(tables.iterator().next()), ms, ds, where);
        } else if (tables.size() == 2) {
            sql = twoTable(model, tables, ms, ds, where);
        } else {
            throw new UnsupportedOperationException("queries spanning more than two tables are not planned yet");
        }
        return applyOrderLimit(sql, ms, ds, spec);
    }

    private static String applyOrderLimit(String sql, List<SemanticModel.Metric> ms,
            List<SemanticModel.Dimension> ds, QuerySpec spec) {
        if (ds.isEmpty() || (spec.orderBy() == null && spec.limit() == null)) {
            return sql;
        }
        String column;
        if (spec.orderBy() != null && !spec.orderBy().isBlank()) {
            String ref = spec.orderBy().trim();
            column = ms.stream().filter(m -> m.ref().equals(ref)).findFirst()
                    .map(SemanticModel.Metric::column)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "orderBy must be one of the selected metrics: " + ref));
        } else {
            column = ms.get(0).column();
        }
        StringBuilder out = new StringBuilder(sql)
                .append(" order by ").append(q(column)).append(spec.descending() ? " desc" : " asc");
        if (spec.limit() != null && spec.limit() > 0) {
            out.append(" limit ").append(spec.limit());
        }
        return out.toString();
    }

    private static String buildWhere(SemanticModel model, QuerySpec spec) {
        List<String> conditions = new ArrayList<>();
        for (Filter f : spec.filters()) {
            Resolved r = resolveRef(model, f.column());
            String op = f.op() == null ? "" : f.op().trim();
            if (!OPERATORS.contains(op)) {
                throw new IllegalArgumentException("unsupported filter operator: " + f.op());
            }
            conditions.add(qcol(r.table(), r.column()) + " " + op + " " + literal(r.numeric(), f.value()));
        }
        if (spec.lastDays() != null && spec.timeColumn() != null && !spec.timeColumn().isBlank()) {
            String ref = spec.timeColumn().trim();
            SemanticModel.TimeColumn tc = model.timeColumn(ref)
                    .orElseThrow(() -> new IllegalArgumentException("not a time column: " + ref));
            // Normalize to a real timestamp per the column's encoding (native DATE / epoch / text), so the
            // window works whether time is a DATE or an epoch long. The DB computes the cutoff from the
            // data's own latest value, so "last N days" is exact and different N give different windows.
            String outer = tc.toTimestamp(qcol(tc.table(), tc.column()));
            String inner = tc.toTimestamp(q(tc.column()));
            conditions.add(outer + " >= (select max(" + inner + ") from " + q(tc.table())
                    + ") - INTERVAL '" + Math.max(0, spec.lastDays()) + " days'");
        }
        return conditions.isEmpty() ? "" : " where " + String.join(" and ", conditions);
    }

    private static String twoTable(SemanticModel model, Set<String> tables,
            List<SemanticModel.Metric> ms, List<SemanticModel.Dimension> ds, String where) {
        Iterator<String> it = tables.iterator();
        String a = it.next();
        String b = it.next();
        Relationship rel = relationshipBetween(model, a, b);
        if (rel == null) {
            throw new IllegalArgumentException("no discovered relationship between " + a + " and " + b);
        }
        if ("shared_key".equals(rel.cardinality())) {
            if (!where.isEmpty()) {
                throw new UnsupportedOperationException("filters across two fact tables are not planned yet");
            }
            return fanOutSafe(ms, ds, rel);
        }
        String from = q(rel.fromTable()) + " join " + q(rel.toTable()) + " on " + joinCondition(rel);
        return groupByQuery(from, ms, ds, where);
    }

    private static String joinCondition(Relationship rel) {
        List<String> conditions = new ArrayList<>();
        for (int i = 0; i < rel.fromColumns().size(); i++) {
            conditions.add(qcol(rel.fromTable(), rel.fromColumns().get(i)) + " = "
                    + qcol(rel.toTable(), rel.toColumns().get(i)));
        }
        return String.join(" and ", conditions);
    }

    private static String fanOutSafe(
            List<SemanticModel.Metric> ms, List<SemanticModel.Dimension> ds, Relationship rel) {
        if (!ds.isEmpty()) {
            throw new UnsupportedOperationException(
                    "dimensioned queries across two fact tables are not planned yet");
        }
        LinkedHashMap<String, List<SemanticModel.Metric>> byTable = new LinkedHashMap<>();
        for (SemanticModel.Metric m : ms) {
            byTable.computeIfAbsent(m.table(), t -> new ArrayList<>()).add(m);
        }
        if (byTable.size() != 2) {
            throw new UnsupportedOperationException("shared-key plan needs metrics from exactly two fact tables");
        }
        Map<String, String> alias = new LinkedHashMap<>();
        List<String> cteDefs = new ArrayList<>();
        int i = 0;
        for (Map.Entry<String, List<SemanticModel.Metric>> e : byTable.entrySet()) {
            String table = e.getKey();
            String a = "f" + (i++);
            alias.put(table, a);
            List<String> keyCols = table.equals(rel.fromTable()) ? rel.fromColumns() : rel.toColumns();
            List<String> keySelect = new ArrayList<>();
            List<String> keyGroup = new ArrayList<>();
            for (int k = 0; k < keyCols.size(); k++) {
                keySelect.add(q(keyCols.get(k)) + " as k" + k);
                keyGroup.add(q(keyCols.get(k)));
            }
            List<String> aggs = new ArrayList<>();
            for (SemanticModel.Metric m : e.getValue()) {
                aggs.add(m.aggregation() + "(" + q(m.column()) + ") as " + q(m.column()));
            }
            cteDefs.add(a + " as (select " + String.join(", ", keySelect) + ", " + String.join(", ", aggs)
                    + " from " + q(table) + " group by " + String.join(", ", keyGroup) + ")");
        }
        List<String> aliasList = new ArrayList<>(alias.values());
        List<String> joinConds = new ArrayList<>();
        for (int k = 0; k < rel.fromColumns().size(); k++) {
            joinConds.add(aliasList.get(0) + ".k" + k + " = " + aliasList.get(1) + ".k" + k);
        }
        String join = aliasList.get(0) + " join " + aliasList.get(1) + " on " + String.join(" and ", joinConds);
        List<String> select = new ArrayList<>();
        for (SemanticModel.Metric m : ms) {
            select.add("sum(" + alias.get(m.table()) + "." + q(m.column()) + ") as " + q(m.column()));
        }
        return "with " + String.join(", ", cteDefs) + " select " + String.join(", ", select) + " from " + join;
    }

    private static String groupByQuery(String from, List<SemanticModel.Metric> ms,
            List<SemanticModel.Dimension> ds, String where) {
        List<String> select = new ArrayList<>();
        for (SemanticModel.Dimension d : ds) {
            select.add(qcol(d.table(), d.column()) + " as " + q(d.column()));
        }
        for (SemanticModel.Metric m : ms) {
            select.add(m.aggregation() + "(" + qcol(m.table(), m.column()) + ") as " + q(m.column()));
        }
        StringBuilder sql = new StringBuilder("select " + String.join(", ", select) + " from " + from + where);
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

    private record Resolved(String table, String column, boolean numeric) {}

    private static Resolved resolveRef(SemanticModel model, String ref) {
        for (SemanticModel.Metric m : model.metrics()) {
            if (m.ref().equals(ref)) {
                return new Resolved(m.table(), m.column(), true);
            }
        }
        for (SemanticModel.Dimension d : model.dimensions()) {
            if (d.ref().equals(ref)) {
                return new Resolved(d.table(), d.column(), false);
            }
        }
        for (String key : model.entityKeys()) {
            if (key.equals(ref)) {
                int dot = ref.indexOf('.');
                return new Resolved(ref.substring(0, dot), ref.substring(dot + 1), false);
            }
        }
        throw new IllegalArgumentException("unknown column: " + ref);
    }

    private static String literal(boolean numeric, String value) {
        String v = value == null ? "" : value.trim();
        if (numeric) {
            try {
                Double.parseDouble(v);
                return v;
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("numeric filter value expected, got: " + value);
            }
        }
        return "'" + v.replace("'", "''") + "'";
    }

    private static String q(String id) {
        return Sql.quote(id);
    }

    private static String qcol(String table, String column) {
        return Sql.quote(table) + "." + Sql.quote(column);
    }
}
