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

    @Test
    void timeWindowAndFiltersActuallyRestrictTheData() throws Exception {
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
        try (CsvSource src = CsvSource.of(tempDir.resolve("f.duckdb"), csvs)) {
            SemanticModel model = SemanticModel.fromProfile(DatasetProfiler.profile(src));

            double all = total(src, QueryPlanner.plan(model,
                    new QueryPlanner.QuerySpec(List.of("events.amount"), List.of())));
            assertEquals(100.0, all, 0.001);

            // max day is 2025-03-31; last 1 day -> only that row (40); last 40 days -> 30 + 40 = 70.
            double last1 = total(src, QueryPlanner.plan(model, new QueryPlanner.QuerySpec(
                    List.of("events.amount"), List.of(), List.of(), 1, "events.day")));
            double last40 = total(src, QueryPlanner.plan(model, new QueryPlanner.QuerySpec(
                    List.of("events.amount"), List.of(), List.of(), 40, "events.day")));
            assertEquals(40.0, last1, 0.001);
            assertEquals(70.0, last40, 0.001);
            assertTrue(last1 < last40 && last40 < all, "different windows must give different totals");

            // value filter on a dimension
            double east = total(src, QueryPlanner.plan(model, new QueryPlanner.QuerySpec(
                    List.of("events.amount"), List.of(),
                    List.of(new QueryPlanner.Filter("events.region", "=", "east")), null, null)));
            assertEquals(40.0, east, 0.001); // 10 + 30
        }
    }

    private static double total(CsvSource src, String sql) {
        return ((Number) src.nativeQuery(sql).get(0).get("amount")).doubleValue();
    }
}
