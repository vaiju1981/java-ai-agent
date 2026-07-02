package dev.vaijanath.aiagent.demos.analytics;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
            Map<String, Set<String>> idLike = Map.of(
                    "visits", Set.of("player_id"), "sessions", Set.of("player_id"));
            List<Relationship> rels = RelationshipDiscovery.discover(src, idLike);
            assertTrue(rels.stream().anyMatch(r ->
                    r.fromColumn().equals("player_id") && r.toColumn().equals("player_id")
                            && r.cardinality().equals("shared_key")),
                    "player_id shared across two facts (neither unique) must be found");
        }
    }
}
