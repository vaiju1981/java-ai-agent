package dev.vaijanath.aiagent.demos.analytics;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Turns a metric/dimension/filter request into read-only SQL over the {@link SemanticModel}.
 * Single-table requests group by directly; multi-table requests use a {@link JoinTree} spanning
 * tree: a star (at most one fact table) joins directly through any number of dimensions, while a
 * two-fact chain pre-aggregates each fact to the shared key's grain via CTEs before joining them
 * (to stay fan-out-safe), with dimensions attachable to either fact. Filters and a DB-computed
 * "last N days" window are applied as a WHERE clause.
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
        Set<String> factTables = new LinkedHashSet<>();
        Set<String> requested = new LinkedHashSet<>();
        // Metrics (fact tables) must be added to `requested` before dimensions: JoinTree.connect roots
        // its BFS at requested.iterator().next(), and factChainJoin's edge-orientation logic depends on
        // a fact table being reachable early in the traversal. Reordering this could silently break the
        // star/fact-chain split.
        ms.forEach(m -> {
            factTables.add(m.table());
            requested.add(m.table());
        });
        ds.forEach(d -> requested.add(d.table()));
        String sql;
        if (requested.size() == 1) {
            sql = groupByQuery(q(requested.iterator().next()), ms, ds, where);
        } else {
            JoinTree tree = JoinTree.connect(model, requested, factTables);
            sql = tree.factTables().size() <= 1
                    ? starJoin(tree, ms, ds, where)
                    : factChainJoin(tree, ms, ds, where);
        }
        return applyOrderLimit(sql, ms, ds, spec);
    }

    private static String starJoin(JoinTree tree, List<SemanticModel.Metric> ms,
            List<SemanticModel.Dimension> ds, String where) {
        StringBuilder from = new StringBuilder(q(tree.root()));
        for (JoinTree.Edge e : tree.edges()) {
            if ("shared_key".equals(e.relationship().cardinality())) {
                throw new UnsupportedOperationException("joining through a many-to-many relationship to reach "
                        + e.child() + " needs pre-aggregation; not planned for a single-fact query");
            }
            from.append(" join ").append(q(e.child())).append(" on ").append(joinCondition(e));
        }
        return groupByQuery(from.toString(), ms, ds, where);
    }

    private static String joinCondition(JoinTree.Edge e) {
        Relationship rel = e.relationship();
        List<String> parentCols = e.parentIsFrom() ? rel.fromColumns() : rel.toColumns();
        List<String> childCols = e.parentIsFrom() ? rel.toColumns() : rel.fromColumns();
        List<String> conditions = new ArrayList<>();
        for (int i = 0; i < parentCols.size(); i++) {
            conditions.add(qcol(e.parent(), parentCols.get(i)) + " = " + qcol(e.child(), childCols.get(i)));
        }
        return String.join(" and ", conditions);
    }

    private static String factChainJoin(JoinTree tree, List<SemanticModel.Metric> ms,
            List<SemanticModel.Dimension> ds, String where) {
        if (!where.isEmpty()) {
            throw new UnsupportedOperationException("filters across multiple fact tables are not planned yet");
        }
        List<String> factTables = tree.tables().stream().filter(tree.factTables()::contains).toList();
        if (factTables.size() != 2) {
            throw new UnsupportedOperationException(
                    "fact-to-fact chains currently support exactly two fact tables (got " + factTables.size() + ")");
        }
        for (SemanticModel.Dimension d : ds) {
            if (factTables.contains(d.table())) {
                throw new UnsupportedOperationException("a fact table pre-aggregated for a shared-key join "
                        + "cannot also supply its own dimension (" + d.ref() + ")");
            }
        }
        JoinTree.Edge factEdge = tree.edges().stream()
                .filter(e -> tree.factTables().contains(e.parent()) && tree.factTables().contains(e.child()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("fact tables are not directly connected by a shared key"));
        Relationship rel = factEdge.relationship();
        if (!"shared_key".equals(rel.cardinality())) {
            throw new IllegalArgumentException("expected a shared-key relationship between "
                    + factEdge.parent() + " and " + factEdge.child());
        }

        Map<String, List<SemanticModel.Metric>> byTable = new LinkedHashMap<>();
        for (SemanticModel.Metric m : ms) {
            byTable.computeIfAbsent(m.table(), t -> new ArrayList<>()).add(m);
        }
        Map<String, String> alias = new LinkedHashMap<>();
        Map<String, List<String>> keyColsByTable = new LinkedHashMap<>();
        List<String> cteDefs = new ArrayList<>();
        int i = 0;
        for (String table : factTables) {
            String a = "f" + (i++);
            alias.put(table, a);
            List<String> keyCols = table.equals(rel.fromTable()) ? rel.fromColumns() : rel.toColumns();
            keyColsByTable.put(table, keyCols);
            List<String> keySelect = new ArrayList<>();
            List<String> keyGroup = new ArrayList<>();
            for (int k = 0; k < keyCols.size(); k++) {
                keySelect.add(q(keyCols.get(k)) + " as k" + k);
                keyGroup.add(q(keyCols.get(k)));
            }
            List<String> aggs = new ArrayList<>();
            for (SemanticModel.Metric m : byTable.getOrDefault(table, List.of())) {
                aggs.add(m.aggregation() + "(" + q(m.column()) + ") as " + q(m.column()));
            }
            String select = aggs.isEmpty() ? String.join(", ", keySelect)
                    : String.join(", ", keySelect) + ", " + String.join(", ", aggs);
            cteDefs.add(a + " as (select " + select + " from " + q(table)
                    + " group by " + String.join(", ", keyGroup) + ")");
        }
        List<String> joinConds = new ArrayList<>();
        for (int k = 0; k < rel.fromColumns().size(); k++) {
            joinConds.add(alias.get(factTables.get(0)) + ".k" + k + " = " + alias.get(factTables.get(1)) + ".k" + k);
        }
        StringBuilder from = new StringBuilder(alias.get(factTables.get(0)) + " join " + alias.get(factTables.get(1))
                + " on " + String.join(" and ", joinConds));
        List<String> select = new ArrayList<>();
        for (SemanticModel.Metric m : ms) {
            select.add("sum(" + alias.get(m.table()) + "." + q(m.column()) + ") as " + q(m.column()));
        }
        List<String> groupBy = new ArrayList<>();
        for (JoinTree.Edge e : tree.edges()) {
            if (factTables.contains(e.parent()) && !factTables.contains(e.child())) {
                List<String> grainCols = keyColsByTable.get(e.parent());
                List<String> parentCols = e.parentIsFrom() ? e.relationship().fromColumns() : e.relationship().toColumns();
                List<String> childCols = e.parentIsFrom() ? e.relationship().toColumns() : e.relationship().fromColumns();
                List<String> onConds = new ArrayList<>();
                for (int k = 0; k < parentCols.size(); k++) {
                    int grainIndex = grainCols.indexOf(parentCols.get(k));
                    if (grainIndex < 0) {
                        throw new IllegalArgumentException("dimension " + e.child() + " joins on "
                                + parentCols.get(k) + ", which is not part of " + e.parent() + "'s shared-key grain");
                    }
                    onConds.add(alias.get(e.parent()) + ".k" + grainIndex + " = " + qcol(e.child(), childCols.get(k)));
                }
                from.append(" join ").append(q(e.child())).append(" on ").append(String.join(" and ", onConds));
            }
        }
        for (SemanticModel.Dimension d : ds) {
            select.add(qcol(d.table(), d.column()) + " as " + q(d.column()));
            groupBy.add(qcol(d.table(), d.column()));
        }
        String sql = "with " + String.join(", ", cteDefs) + " select " + String.join(", ", select) + " from " + from;
        return groupBy.isEmpty() ? sql : sql + " group by " + String.join(", ", groupBy);
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
