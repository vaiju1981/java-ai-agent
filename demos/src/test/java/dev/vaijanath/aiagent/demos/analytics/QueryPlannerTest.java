package dev.vaijanath.aiagent.demos.analytics;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class QueryPlannerTest {

    @TempDir
    Path tempDir;

    @Test
    void singleTableGroupBy() throws Exception {
        try (CsvSource src = source()) {
            SemanticModel model = SemanticModel.fromProfile(DatasetProfiler.profile(src));
            String sql = QueryPlanner.plan(model,
                    new QueryPlanner.QuerySpec(List.of("orders.revenue"), List.of("orders.ordered_at")));
            double total = src.nativeQuery(sql).stream()
                    .mapToDouble(r -> ((Number) r.get("revenue")).doubleValue()).sum();
            assertEquals(995.5, total, 0.001); // 250 + 90.5 + 45 + 610
        }
    }

    @Test
    void manyToOneJoinDoesNotFanOut() throws Exception {
        try (CsvSource src = source()) {
            SemanticModel model = SemanticModel.fromProfile(DatasetProfiler.profile(src));
            String sql = QueryPlanner.plan(model,
                    new QueryPlanner.QuerySpec(List.of("orders.revenue"), List.of("customers.segment")));
            double enterprise = src.nativeQuery(sql).stream()
                    .filter(r -> "enterprise".equals(String.valueOf(r.get("segment"))))
                    .mapToDouble(r -> ((Number) r.get("revenue")).doubleValue()).sum();
            assertEquals(950.5, enterprise, 0.001); // cust1 (250+90.5) + cust3 (610), each order once
        }
    }

    private CsvSource source() throws Exception {
        Map<String, Path> csvs = new LinkedHashMap<>();
        csvs.put("customers", Fixtures.customers(tempDir));
        csvs.put("orders", Fixtures.orders(tempDir));
        return CsvSource.of(tempDir.resolve("qp.duckdb"), csvs);
    }
}
