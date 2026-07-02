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
        // Two fact tables sharing player_id (neither unique on it). Player 7 has 2 visits and 2
        // sessions; a naive join yields 2x2 rows and would inflate sums. True totals: coin_in=35,
        // net_win=10. A naive join would report coin_in=70, net_win=17.
        Path visits = tempDir.resolve("visits.csv");
        Files.writeString(visits, "player_id,coin_in\n7,10.0\n7,20.0\n8,5.0\n");
        Path sessions = tempDir.resolve("sessions.csv");
        Files.writeString(sessions, "player_id,net_win\n7,3.0\n7,4.0\n8,1.0\n8,2.0\n");

        Map<String, Path> csvs = new LinkedHashMap<>();
        csvs.put("visits", visits);
        csvs.put("sessions", sessions);
        try (CsvSource src = CsvSource.of(tempDir.resolve("fos.duckdb"), csvs)) {
            SemanticModel model = SemanticModel.fromProfile(DatasetProfiler.profile(src));
            String sql = QueryPlanner.plan(model,
                    new QueryPlanner.QuerySpec(List.of("visits.coin_in", "sessions.net_win"), List.of()));
            Map<String, Object> row = src.nativeQuery(sql).get(0);
            assertEquals(35.0, ((Number) row.get("coin_in")).doubleValue(), 0.001);
            assertEquals(10.0, ((Number) row.get("net_win")).doubleValue(), 0.001);
        }
    }
}
