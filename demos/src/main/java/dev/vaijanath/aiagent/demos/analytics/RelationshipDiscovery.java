package dev.vaijanath.aiagent.demos.analytics;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class RelationshipDiscovery {

    private static final double MIN_COVERAGE = 0.8;
    private RelationshipDiscovery() {}

    static List<Relationship> discover(DataSource src, Map<String, Map<String, ColumnRole>> rolesByTable) {
        return discover(src, rolesByTable, Map.of());
    }

    static List<Relationship> discover(DataSource src, Map<String, Map<String, ColumnRole>> rolesByTable,
            Map<String, ColumnProfile> statsByRef) {
        List<TableSchema> tables = src.tables();
        List<Relationship> out = new ArrayList<>();
        for (TableSchema left : tables) {
            for (TableSchema right : tables) {
                if (left.name().compareTo(right.name()) >= 0) {
                    continue; // each unordered pair once
                }
                for (ColumnSchema lc : left.columns()) {
                    for (ColumnSchema rc : right.columns()) {
                        if (!candidate(left, lc, right, rc, rolesByTable)) {
                            continue;
                        }
                        Coverage cov = coverageOf(src, left.name(), List.of(lc.name()),
                                right.name(), List.of(rc.name()), statsByRef);
                        if (cov.ratio() >= MIN_COVERAGE) {
                            out.add(cardinalityRelationship(left, List.of(lc.name()), right, List.of(rc.name()), cov));
                        }
                    }
                }
            }
        }
        return out;
    }

    private static boolean candidate(TableSchema lt, ColumnSchema lc, TableSchema rt, ColumnSchema rc,
            Map<String, Map<String, ColumnRole>> rolesByTable) {
        ColumnRole lr = roleOf(rolesByTable, lt.name(), lc.name());
        ColumnRole rr = roleOf(rolesByTable, rt.name(), rc.name());
        // You join on keys/dimensions, never on measures: two fact tables sharing a metric name
        // (coinIn, netWin) have overlapping value ranges, but that is not a relationship.
        if (isMeasure(lr) || isMeasure(rr)) {
            return false;
        }
        if (ColumnProfiler.isNumeric(lc.physicalType()) != ColumnProfiler.isNumeric(rc.physicalType())) {
            return false; // never intersect a text column with a numeric one
        }
        String ln = lc.name();
        String rn = rc.name();
        boolean bothId = lr == ColumnRole.IDENTIFIER && rr == ColumnRole.IDENTIFIER;
        return ln.equalsIgnoreCase(rn) || bothId
                || ln.equalsIgnoreCase(rt.name() + "_id") || rn.equalsIgnoreCase(lt.name() + "_id");
    }

    private static boolean isMeasure(ColumnRole r) {
        return r == ColumnRole.MEASURE_ADDITIVE || r == ColumnRole.MEASURE_RATIO;
    }

    private static ColumnRole roleOf(Map<String, Map<String, ColumnRole>> roles, String table, String col) {
        return roles.getOrDefault(table, Map.of()).getOrDefault(col, ColumnRole.UNKNOWN);
    }

    record Coverage(long leftDistinct, long rightDistinct, long overlap) {
        double ratio() {
            return leftDistinct == 0 || rightDistinct == 0 ? -1.0
                    : (double) overlap / Math.min(leftDistinct, rightDistinct);
        }
    }

    private static Coverage coverageOf(DataSource src, String lt, List<String> lc, String rt, List<String> rc,
            Map<String, ColumnProfile> statsByRef) {
        long ld = distinctOf(src, lt, lc, statsByRef);
        long rd = distinctOf(src, rt, rc, statsByRef);
        long overlap = (ld == 0 || rd == 0) ? 0 : overlapOf(src, lt, lc, rt, rc);
        return new Coverage(ld, rd, overlap);
    }

    private static long distinctOf(DataSource src, String table, List<String> cols,
            Map<String, ColumnProfile> statsByRef) {
        if (cols.size() == 1) {
            ColumnProfile cp = statsByRef.get(table + "." + cols.get(0));
            if (cp != null) {
                return cp.distinct();
            }
        }
        String select = cols.stream().map(Sql::quote).reduce((a, b) -> a + ", " + b).orElseThrow();
        String whereNotNull = cols.stream().map(c -> Sql.quote(c) + " is not null")
                .reduce((a, b) -> a + " and " + b).orElseThrow();
        return ((Number) src.nativeQuery("select count(*) d from (select distinct " + select
                + " from " + Sql.quote(table) + " where " + whereNotNull + ")").get(0).get("d")).longValue();
    }

    private static long overlapOf(DataSource src, String lt, List<String> lc, String rt, List<String> rc) {
        String lSelect = lc.stream().map(Sql::quote).reduce((a, b) -> a + ", " + b).orElseThrow();
        String rSelect = rc.stream().map(Sql::quote).reduce((a, b) -> a + ", " + b).orElseThrow();
        String lWhere = lc.stream().map(c -> Sql.quote(c) + " is not null")
                .reduce((a, b) -> a + " and " + b).orElseThrow();
        String rWhere = rc.stream().map(c -> Sql.quote(c) + " is not null")
                .reduce((a, b) -> a + " and " + b).orElseThrow();
        return ((Number) src.nativeQuery("select count(*) n from (select distinct " + lSelect + " from "
                + Sql.quote(lt) + " where " + lWhere + " intersect select distinct " + rSelect + " from "
                + Sql.quote(rt) + " where " + rWhere + ")").get(0).get("n")).longValue();
    }

    private static Relationship cardinalityRelationship(TableSchema lt, List<String> lc, TableSchema rt,
            List<String> rc, Coverage cov) {
        boolean lUnique = cov.leftDistinct() == lt.rows();
        boolean rUnique = cov.rightDistinct() == rt.rows();
        double c = round(cov.ratio());
        if (rUnique && !lUnique) {
            return new Relationship(lt.name(), lc, rt.name(), rc, "many_to_one", c);
        }
        if (lUnique && !rUnique) {
            return new Relationship(rt.name(), rc, lt.name(), lc, "many_to_one", c);
        }
        if (lUnique) {
            return new Relationship(lt.name(), lc, rt.name(), rc, "one_to_one", c);
        }
        return new Relationship(lt.name(), lc, rt.name(), rc, "shared_key", c);
    }

    private static double round(double v) {
        return Math.round(v * 1000.0) / 1000.0;
    }
}
