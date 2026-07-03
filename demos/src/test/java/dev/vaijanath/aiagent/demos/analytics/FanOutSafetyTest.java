package dev.vaijanath.aiagent.demos.analytics;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FanOutSafetyTest {

    @TempDir
    Path tempDir;

    @Test
    void twoFactSharedKeyDoesNotDoubleCount() throws Exception {
        // Two fact tables sharing customer_id (neither unique on it). Customer 7 has 2 visits and 2
        // sessions; a naive join yields 2x2 rows and would inflate sums. True totals: spend=35,
        // revenue=10. A naive join would report spend=70, revenue=17.
        Path visits = tempDir.resolve("visits.csv");
        Files.writeString(visits, "customer_id,spend\n7,10.0\n7,20.0\n8,5.0\n");
        Path sessions = tempDir.resolve("sessions.csv");
        Files.writeString(sessions, "customer_id,revenue\n7,3.0\n7,4.0\n8,1.0\n8,2.0\n");

        Map<String, Path> csvs = new LinkedHashMap<>();
        csvs.put("visits", visits);
        csvs.put("sessions", sessions);
        try (CsvSource src = CsvSource.of(tempDir.resolve("fos.duckdb"), csvs)) {
            SemanticModel model = SemanticModel.fromProfile(DatasetProfiler.profile(src));
            String sql = QueryPlanner.plan(model,
                    new QueryPlanner.QuerySpec(List.of("visits.spend", "sessions.revenue"), List.of()));
            Map<String, Object> row = src.nativeQuery(sql).get(0);
            assertEquals(35.0, ((Number) row.get("spend")).doubleValue(), 0.001);
            assertEquals(10.0, ((Number) row.get("revenue")).doubleValue(), 0.001);
        }
    }
}
