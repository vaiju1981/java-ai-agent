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
            Map<String, Map<String, ColumnRole>> roles = Map.of(
                    "visits", Map.of("visit_id", ColumnRole.IDENTIFIER,
                            "player_id", ColumnRole.IDENTIFIER, "coin_in", ColumnRole.MEASURE_ADDITIVE),
                    "sessions", Map.of("session_id", ColumnRole.IDENTIFIER,
                            "player_id", ColumnRole.IDENTIFIER, "net_win", ColumnRole.MEASURE_ADDITIVE));
            List<Relationship> rels = RelationshipDiscovery.discover(src, roles, Map.of());
            assertTrue(rels.stream().anyMatch(r ->
                    r.fromColumn().equals("player_id") && r.toColumn().equals("player_id")
                            && r.cardinality().equals("shared_key")),
                    "player_id shared across two facts (neither unique) must be found");
        }
    }

    @Test
    void findsATwoColumnCompositeKeyAndSupersedesTheCoarseSingleColumnMatch() throws Exception {
        // Neither "region" nor "year" alone is a tight key (each has only 2 distinct values across 4
        // rows on both sides -- a real, fully-overlapping, but coarse shared_key match on its own).
        // Only the (region, year) pair uniquely identifies each row on both sides.
        Path sales = tempDir.resolve("sales.csv");
        Files.writeString(sales, "region,year,amount\neast,2024,10\neast,2025,20\nwest,2024,30\nwest,2025,40\n");
        Path targets = tempDir.resolve("targets.csv");
        Files.writeString(targets, "region,year,plan\neast,2024,5\neast,2025,15\nwest,2024,25\nwest,2025,35\n");

        Map<String, Path> csvs = new LinkedHashMap<>();
        csvs.put("sales", sales);
        csvs.put("targets", targets);
        try (CsvSource src = CsvSource.of(tempDir.resolve("ck2.duckdb"), csvs)) {
            Map<String, Map<String, ColumnRole>> roles = Map.of(
                    "sales", Map.of("region", ColumnRole.DIMENSION, "year", ColumnRole.DIMENSION,
                            "amount", ColumnRole.MEASURE_ADDITIVE),
                    "targets", Map.of("region", ColumnRole.DIMENSION, "year", ColumnRole.DIMENSION,
                            "plan", ColumnRole.MEASURE_ADDITIVE));
            List<Relationship> rels = RelationshipDiscovery.discover(src, roles, Map.of());

            assertTrue(rels.stream().anyMatch(r ->
                    Set.copyOf(r.fromColumns()).equals(Set.of("region", "year"))
                            && Set.copyOf(r.toColumns()).equals(Set.of("region", "year"))
                            && r.cardinality().equals("one_to_one")),
                    "composite (region, year) key must be found: " + rels);
            assertTrue(rels.stream().noneMatch(r -> r.fromColumns().equals(List.of("region"))),
                    "coarse standalone 'region' match must be superseded by the tighter composite: " + rels);
            assertTrue(rels.stream().noneMatch(r -> r.fromColumns().equals(List.of("year"))),
                    "coarse standalone 'year' match must be superseded by the tighter composite: " + rels);
        }
    }

    @Test
    void doesNotInventACompositeWhenNoSecondColumnTightensTheJoin() throws Exception {
        // "player_id" is a genuine shared_key on its own (visit_id/session_id are unrelated identifiers,
        // not composite-key material) -- the existing single-column relationship must survive untouched.
        Path visits = tempDir.resolve("visits2.csv");
        Files.writeString(visits, "visit_id,player_id,coin_in\nv1,7,10\nv2,7,20\nv3,8,5\n");
        Path sessions = tempDir.resolve("sessions2.csv");
        Files.writeString(sessions, "session_id,player_id,net_win\ns1,7,3\ns2,7,4\ns3,8,1\ns4,8,2\n");

        Map<String, Path> csvs = new LinkedHashMap<>();
        csvs.put("visits", visits);
        csvs.put("sessions", sessions);
        try (CsvSource src = CsvSource.of(tempDir.resolve("ck3.duckdb"), csvs)) {
            Map<String, Map<String, ColumnRole>> roles = Map.of(
                    "visits", Map.of("visit_id", ColumnRole.IDENTIFIER, "player_id", ColumnRole.IDENTIFIER,
                            "coin_in", ColumnRole.MEASURE_ADDITIVE),
                    "sessions", Map.of("session_id", ColumnRole.IDENTIFIER, "player_id", ColumnRole.IDENTIFIER,
                            "net_win", ColumnRole.MEASURE_ADDITIVE));
            List<Relationship> rels = RelationshipDiscovery.discover(src, roles, Map.of());

            assertTrue(rels.stream().anyMatch(r ->
                    r.fromColumns().equals(List.of("player_id")) && r.cardinality().equals("shared_key")),
                    "player_id must remain a standalone shared_key relationship: " + rels);
            assertTrue(rels.stream().noneMatch(r -> r.fromColumns().size() > 1),
                    "no composite should be invented when nothing tightens the join: " + rels);
        }
    }
}
