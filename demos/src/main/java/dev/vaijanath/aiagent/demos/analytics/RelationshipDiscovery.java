package dev.vaijanath.aiagent.demos.analytics;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Discovers join relationships between tables, including composite (multi-column) keys.
 * A single-column match that isn't a confident key becomes a "seed" that {@link #discoverComposite}
 * pairs with a second column to search for a tighter join. Composite search is currently capped at
 * {@link #MAX_COMPOSITE_KEY_SIZE} (2) columns; widening it requires generalizing the single extra-column
 * loop in {@code discoverComposite} into a combinations-of-size-(MAX-1) loop.
 */
final class RelationshipDiscovery {

    private static final double MIN_COVERAGE = 0.8;
    private static final double MIN_PARTIAL_COVERAGE = 0.05;
    // A composite key is two columns together; raising past 2 needs generalizing discoverComposite's
    // single extra-column loop into a combinations-of-size-(MAX-1) loop, not a redesign.
    private static final int MAX_COMPOSITE_KEY_SIZE = 2;

    private RelationshipDiscovery() {}

    private record PartialMatch(TableSchema left, ColumnSchema leftCol, TableSchema right, ColumnSchema rightCol) {}

    static List<Relationship> discover(DataSource src, Map<String, Map<String, ColumnRole>> rolesByTable) {
        return discover(src, rolesByTable, Map.of());
    }

    static List<Relationship> discover(DataSource src, Map<String, Map<String, ColumnRole>> rolesByTable,
            Map<String, ColumnProfile> statsByRef) {
        List<TableSchema> tables = src.tables();
        List<Relationship> confident = new ArrayList<>();
        Map<String, Relationship> coarseByTablePair = new LinkedHashMap<>();
        Map<String, List<PartialMatch>> seeds = new LinkedHashMap<>();
        for (TableSchema left : tables) {
            for (TableSchema right : tables) {
                if (left.name().compareTo(right.name()) >= 0) {
                    continue; // each unordered pair once
                }
                String pairKey = left.name() + "|" + right.name();
                for (ColumnSchema lc : left.columns()) {
                    for (ColumnSchema rc : right.columns()) {
                        if (!candidate(left, lc, right, rc, rolesByTable)) {
                            continue;
                        }
                        Coverage cov = coverageOf(src, left.name(), List.of(lc.name()),
                                right.name(), List.of(rc.name()), statsByRef);
                        double ratio = cov.ratio();
                        if (ratio >= MIN_COVERAGE) {
                            Relationship rel = cardinalityRelationship(left, List.of(lc.name()), right, List.of(rc.name()), cov);
                            if ("shared_key".equals(rel.cardinality())) {
                                coarseByTablePair.putIfAbsent(pairKey, rel);
                                seeds.computeIfAbsent(pairKey, k -> new ArrayList<>()).add(new PartialMatch(left, lc, right, rc));
                            } else {
                                confident.add(rel);
                            }
                        } else if (ratio >= MIN_PARTIAL_COVERAGE) {
                            seeds.computeIfAbsent(pairKey, k -> new ArrayList<>()).add(new PartialMatch(left, lc, right, rc));
                        }
                    }
                }
            }
        }
        List<Relationship> composites = discoverComposite(src, rolesByTable, seeds, statsByRef);
        Set<String> superseded = new java.util.HashSet<>();
        composites.forEach(c -> superseded.add(c.fromTable() + "|" + c.toTable()));

        List<Relationship> out = new ArrayList<>(confident);
        coarseByTablePair.forEach((pair, rel) -> {
            if (!superseded.contains(pair)) {
                out.add(rel);
            }
        });
        out.addAll(composites);
        return out;
    }

    private static List<Relationship> discoverComposite(DataSource src,
            Map<String, Map<String, ColumnRole>> rolesByTable, Map<String, List<PartialMatch>> seedsByTablePair,
            Map<String, ColumnProfile> statsByRef) {
        List<Relationship> out = new ArrayList<>();
        Set<String> seen = new java.util.LinkedHashSet<>();
        for (List<PartialMatch> seeds : seedsByTablePair.values()) {
            for (PartialMatch seed : seeds) {
                TableSchema left = seed.left();
                TableSchema right = seed.right();
                for (ColumnSchema lc2 : left.columns()) {
                    if (lc2.name().equals(seed.leftCol().name())) {
                        continue;
                    }
                    for (ColumnSchema rc2 : right.columns()) {
                        if (rc2.name().equals(seed.rightCol().name()) || !candidate(left, lc2, right, rc2, rolesByTable)) {
                            continue;
                        }
                        List<String> fromCols = new ArrayList<>(List.of(seed.leftCol().name(), lc2.name()));
                        List<String> toCols = new ArrayList<>(List.of(seed.rightCol().name(), rc2.name()));
                        if (fromCols.get(0).compareTo(fromCols.get(1)) > 0) {
                            java.util.Collections.reverse(fromCols);
                            java.util.Collections.reverse(toCols);
                        }
                        String key = left.name() + fromCols + ">" + right.name() + toCols;
                        if (!seen.add(key)) {
                            continue;
                        }
                        Coverage cov = coverageOf(src, left.name(), fromCols, right.name(), toCols, statsByRef);
                        if (cov.ratio() >= MIN_COVERAGE) {
                            out.add(cardinalityRelationship(left, fromCols, right, toCols, cov));
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
