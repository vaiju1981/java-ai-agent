package dev.vaijanath.aiagent.demos.analytics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class QueryPlannerFilterTest {

    @TempDir
    Path tempDir;

    private CsvSource events() throws Exception {
        Path events = tempDir.resolve("events.csv");
        Files.writeString(events, """
                day,region,amount
                2025-01-01,east,10
                2025-01-10,west,20
                2025-03-01,east,30
                2025-03-31,west,40
                """);
        Map<String, Path> csvs = new LinkedHashMap<>();
        csvs.put("events", events);
        return CsvSource.of(tempDir.resolve("f.duckdb"), csvs);
    }

    private static SemanticModel model(CsvSource src) {
        return SemanticModel.fromProfile(DatasetProfiler.profile(src));
    }

    private static double total(CsvSource src, String sql) {
        return ((Number) src.nativeQuery(sql).get(0).get("amount")).doubleValue();
    }

    @Test
    void timeWindowRestrictsData() throws Exception {
        try (CsvSource src = events()) {
            SemanticModel m = model(src);
            double all = total(src, QueryPlanner.plan(m,
                    new QueryPlanner.QuerySpec(List.of("events.amount"), List.of())));
            double last1 = total(src, QueryPlanner.plan(m, new QueryPlanner.QuerySpec(
                    List.of("events.amount"), List.of(), List.of(), 1, "events.day")));
            double last40 = total(src, QueryPlanner.plan(m, new QueryPlanner.QuerySpec(
                    List.of("events.amount"), List.of(), List.of(), 40, "events.day")));
            assertEquals(100.0, all, 0.001);
            assertEquals(40.0, last1, 0.001);
            assertEquals(70.0, last40, 0.001);
            assertTrue(last1 < last40 && last40 < all, "different windows -> different totals");
        }
    }

    @Test
    void multipleFiltersCombineWithAnd() throws Exception {
        try (CsvSource src = events()) {
            String sql = QueryPlanner.plan(model(src), new QueryPlanner.QuerySpec(
                    List.of("events.amount"), List.of(),
                    List.of(new QueryPlanner.Filter("events.region", "=", "east"),
                            new QueryPlanner.Filter("events.amount", ">", "15")),
                    null, null));
            assertEquals(30.0, total(src, sql), 0.001); // east AND amount>15 -> only the 30 row
        }
    }

    @Test
    void topNOrdersAndLimits() throws Exception {
        try (CsvSource src = events()) {
            String sql = QueryPlanner.plan(model(src), new QueryPlanner.QuerySpec(
                    List.of("events.amount"), List.of("events.region"),
                    List.of(), null, null, null, true, 1));
            List<Map<String, Object>> rows = src.nativeQuery(sql);
            assertEquals(1, rows.size(), "limit 1");
            assertEquals("west", String.valueOf(rows.get(0).get("region"))); // west 60 > east 40
        }
    }

    @Test
    void offsetDaysShiftsTimeWindowWithoutChangingDefault() throws Exception {
        Path events = tempDir.resolve("periods.csv");
        Files.writeString(events, """
                day,amount
                2025-02-10,10
                2025-02-20,20
                2025-03-10,30
                2025-03-31,40
                """);
        Map<String, Path> csvs = new LinkedHashMap<>();
        csvs.put("periods", events);
        try (CsvSource src = CsvSource.of(tempDir.resolve("periods.duckdb"), csvs)) {
            SemanticModel m = model(src);
            String defaultSql = QueryPlanner.plan(m, new QueryPlanner.QuerySpec(
                    List.of("periods.amount"), List.of(), List.of(), 30, "periods.day"));
            String explicitZeroSql = QueryPlanner.plan(m, new QueryPlanner.QuerySpec(
                    List.of("periods.amount"), List.of(), List.of(), 30, "periods.day", null, true, null, 0));
            String previousSql = QueryPlanner.plan(m, new QueryPlanner.QuerySpec(
                    List.of("periods.amount"), List.of(), List.of(), 30, "periods.day", null, true, null, 30));

            assertEquals(defaultSql, explicitZeroSql, "offsetDays=0 preserves existing run_query SQL");
            assertEquals(70.0, total(src, defaultSql), 0.001); // Mar 10 + Mar 31
            assertEquals(30.0, total(src, previousSql), 0.001); // Feb 10 + Feb 20, excludes Mar 10+
        }
    }
}
