package dev.vaijanath.aiagent.demos.analytics;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class RelationshipDiscovery {

    private static final double MIN_COVERAGE = 0.8;

    private RelationshipDiscovery() {}

    static List<Relationship> discover(CsvSource src, Map<String, Map<String, ColumnRole>> rolesByTable) {
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
                        Relationship r = evaluate(src, left, lc, right, rc);
                        if (r != null) {
                            out.add(r);
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

    private static Relationship evaluate(CsvSource src, TableSchema lt, ColumnSchema lc,
            TableSchema rt, ColumnSchema rc) {
        long ld = distinct(src, lt.name(), lc.name());
        long rd = distinct(src, rt.name(), rc.name());
        if (ld == 0 || rd == 0) {
            return null;
        }
        long overlap = overlap(src, lt.name(), lc.name(), rt.name(), rc.name());
        double cov = (double) overlap / Math.min(ld, rd);
        if (cov < MIN_COVERAGE) {
            return null;
        }
        boolean lUnique = ld == lt.rows();
        boolean rUnique = rd == rt.rows();
        if (rUnique && !lUnique) {
            return new Relationship(lt.name(), lc.name(), rt.name(), rc.name(), "many_to_one", round(cov));
        }
        if (lUnique && !rUnique) {
            return new Relationship(rt.name(), rc.name(), lt.name(), lc.name(), "many_to_one", round(cov));
        }
        if (lUnique) {
            return new Relationship(lt.name(), lc.name(), rt.name(), rc.name(), "one_to_one", round(cov));
        }
        // Neither side unique + high overlap on key/dimension columns = conformed key shared across facts.
        return new Relationship(lt.name(), lc.name(), rt.name(), rc.name(), "shared_key", round(cov));
    }

    private static long distinct(CsvSource src, String table, String col) {
        return ((Number) src.nativeQuery("select count(distinct " + CsvSource.quote(col) + ") d from "
                + CsvSource.quote(table) + " where " + CsvSource.quote(col) + " is not null")
                .get(0).get("d")).longValue();
    }

    private static long overlap(CsvSource src, String lt, String lc, String rt, String rc) {
        return ((Number) src.nativeQuery(
                "select count(*) n from (select distinct " + CsvSource.quote(lc) + " v from " + CsvSource.quote(lt)
                        + " where " + CsvSource.quote(lc) + " is not null intersect select distinct "
                        + CsvSource.quote(rc) + " from " + CsvSource.quote(rt)
                        + " where " + CsvSource.quote(rc) + " is not null)")
                .get(0).get("n")).longValue();
    }

    private static double round(double v) {
        return Math.round(v * 1000.0) / 1000.0;
    }
}
