package dev.vaijanath.aiagent.demos.analytics;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class QueryPlannerCompositeKeyTest {

    @TempDir
    Path tempDir;

    @Test
    void joinsOnATwoColumnCompositeKey() throws Exception {
        Path sales = tempDir.resolve("sales.csv");
        Files.writeString(sales, "year,region,amount\n2024,east,10\n2024,west,20\n2025,east,30\n");
        Path targets = tempDir.resolve("targets.csv");
        Files.writeString(targets, "year,region,target\n2024,east,5\n2024,west,15\n2025,east,25\n");

        Map<String, Path> csvs = new LinkedHashMap<>();
        csvs.put("sales", sales);
        csvs.put("targets", targets);
        try (CsvSource src = CsvSource.of(tempDir.resolve("ck.duckdb"), csvs)) {
            SemanticModel base = SemanticModel.fromProfile(DatasetProfiler.profile(src));
            Relationship composite = new Relationship(
                    "sales", List.of("year", "region"), "targets", List.of("year", "region"),
                    "shared_key", 1.0);
            SemanticModel model = new SemanticModel(base.metrics(), base.dimensions(),
                    base.entityKeys(), base.timeColumns(), List.of(composite));

            String sql = QueryPlanner.plan(model, new QueryPlanner.QuerySpec(
                    List.of("sales.amount", "targets.target"), List.of()));
            Map<String, Object> row = src.nativeQuery(sql).get(0);
            assertEquals(60.0, ((Number) row.get("amount")).doubleValue(), 0.001);
            assertEquals(45.0, ((Number) row.get("target")).doubleValue(), 0.001);
        }
    }
}
