package dev.vaijanath.aiagent.demos.analytics;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RelationshipDiscoveryTest {

    @TempDir
    Path tempDir;

    @Test
    void findsSharedKeyBetweenTwoFactTablesWhereNeitherIsUnique() throws Exception {
        Path visits = tempDir.resolve("visits.csv");
        Files.writeString(visits, "visit_id,player_id,coin_in\nv1,7,10\nv2,7,20\nv3,8,5\n");
        Path sessions = tempDir.resolve("sessions.csv");
        Files.writeString(sessions, "session_id,player_id,net_win\ns1,7,3\ns2,7,4\ns3,8,1\ns4,8,2\n");

        Map<String, Path> csvs = new LinkedHashMap<>();
        csvs.put("visits", visits);
        csvs.put("sessions", sessions);
        try (CsvSource src = CsvSource.of(tempDir.resolve("r.duckdb"), csvs)) {
            Map<String, Map<String, ColumnRole>> roles = Map.of(
                    "visits", Map.of("visit_id", ColumnRole.IDENTIFIER,
                            "player_id", ColumnRole.IDENTIFIER, "coin_in", ColumnRole.MEASURE_ADDITIVE),
                    "sessions", Map.of("session_id", ColumnRole.IDENTIFIER,
                            "player_id", ColumnRole.IDENTIFIER, "net_win", ColumnRole.MEASURE_ADDITIVE));
            List<Relationship> rels = RelationshipDiscovery.discover(src, roles);
            assertTrue(rels.stream().anyMatch(r ->
                    r.fromColumn().equals("player_id") && r.toColumn().equals("player_id")
                            && r.cardinality().equals("shared_key")),
                    "player_id shared across two facts (neither unique) must be found");
        }
    }
}
