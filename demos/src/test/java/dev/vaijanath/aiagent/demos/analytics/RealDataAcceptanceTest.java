package dev.vaijanath.aiagent.demos.analytics;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@Tag("manual")
class RealDataAcceptanceTest {

    @TempDir
    Path tempDir;

    @Test
    void profilesRealRedrockFilesAndLinksOnEntityKeys() {
        String dir = System.getenv("ANALYTICS_REAL_DATA_DIR");
        assumeTrue(dir != null && Files.isDirectory(Path.of(dir)),
                "set ANALYTICS_REAL_DATA_DIR to the training_data folder to run");

        Map<String, Path> csvs = new LinkedHashMap<>();
        csvs.put("playerVisits", Path.of(dir, "STATION_playerVisits_REDROCK.csv"));
        csvs.put("sessions", Path.of(dir, "STATION_sessions_REDROCK.csv"));
        csvs.put("assetDaily", Path.of(dir, "STATION_assetDaily_REDROCK.csv"));

        try (CsvSource src = CsvSource.of(tempDir.resolve("real.duckdb"), csvs)) {
            DatasetProfile profile = DatasetProfiler.profile(src);
            System.out.println(profile.toJson());
            assertTrue(profile.tables().stream().anyMatch(t -> t.rows() > 1_000_000),
                    "at least one real table has >1M rows");
            assertTrue(profile.relationships().stream().anyMatch(r ->
                    r.fromColumn().toLowerCase().contains("playerid")
                            || r.fromColumn().toLowerCase().contains("assetid")),
                    "entity-key relationship discovered on playerId/assetId");

            // M2: the planner answers a real grouped question on the real data.
            SemanticModel model = SemanticModel.fromProfile(profile);
            String sql = QueryPlanner.plan(model, new QueryPlanner.QuerySpec(
                    List.of("playervisits.coinIn"), List.of("playervisits.clubLevel")));
            List<Map<String, Object>> byClub = src.nativeQuery(sql);
            System.out.println("coinIn by clubLevel: " + byClub);
            assertTrue(!byClub.isEmpty(), "planner answered a real grouped query");
        }
    }

    @Test
    void discoversCompositeKeysAcrossFourRealTablesWithinATimeBudget() {
        String dir = System.getenv("ANALYTICS_REAL_DATA_DIR");
        assumeTrue(dir != null && Files.isDirectory(Path.of(dir)),
                "set ANALYTICS_REAL_DATA_DIR to the training_data folder to run");

        Map<String, Path> csvs = new LinkedHashMap<>();
        csvs.put("playerVisits", Path.of(dir, "STATION_playerVisits_REDROCK.csv"));
        csvs.put("sessions", Path.of(dir, "STATION_sessions_REDROCK.csv"));
        csvs.put("assetDaily", Path.of(dir, "STATION_assetDaily_REDROCK.csv"));
        csvs.put("changeLog", Path.of(dir, "changeLog_REDROCK.csv"));

        try (CsvSource src = CsvSource.of(tempDir.resolve("real4.duckdb"), csvs)) {
            long start = System.currentTimeMillis();
            DatasetProfile profile = DatasetProfiler.profile(src);
            long elapsedMs = System.currentTimeMillis() - start;
            System.out.println("profiled 4 real tables (composite discovery included) in " + elapsedMs + "ms");
            assertTrue(elapsedMs < 180_000, "composite discovery must stay within a 3 minute budget on real data");

            assertTrue(profile.relationships().stream().anyMatch(r -> r.fromColumns().size() > 1
                    || r.toColumns().size() > 1), "at least one real composite key should be found: "
                    + profile.relationships());
        }
    }
}
