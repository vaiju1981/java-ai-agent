package dev.vaijanath.aiagent.demos.analytics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class QueryPlannerNTableTest {

    @TempDir
    Path tempDir;

    @Test
    void starSchemaJoinsAFactToTwoDimensions() throws Exception {
        Path orders = tempDir.resolve("orders3.csv");
        Files.writeString(orders, "order_id,customer_id,product_id,revenue\n"
                + "1,1,10,100\n2,1,11,50\n3,2,10,75\n");
        Path customers = tempDir.resolve("customers3.csv");
        Files.writeString(customers, "customer_id,segment\n1,enterprise\n2,smb\n");
        Path products = tempDir.resolve("products3.csv");
        Files.writeString(products, "product_id,category\n10,widgets\n11,gadgets\n");

        Map<String, Path> csvs = new LinkedHashMap<>();
        csvs.put("orders", orders);
        csvs.put("customers", customers);
        csvs.put("products", products);
        try (CsvSource src = CsvSource.of(tempDir.resolve("star.duckdb"), csvs)) {
            SemanticModel model = SemanticModel.fromProfile(DatasetProfiler.profile(src));
            String sql = QueryPlanner.plan(model, new QueryPlanner.QuerySpec(
                    List.of("orders.revenue"), List.of("customers.segment", "products.category")));
            List<Map<String, Object>> rows = src.nativeQuery(sql);
            double widgetsEnterprise = rows.stream()
                    .filter(r -> "enterprise".equals(String.valueOf(r.get("segment")))
                            && "widgets".equals(String.valueOf(r.get("category"))))
                    .mapToDouble(r -> ((Number) r.get("revenue")).doubleValue()).sum();
            assertEquals(100.0, widgetsEnterprise, 0.001);
        }
    }

    @Test
    void factChainAttachesADimensionToOneOfTheTwoFacts() throws Exception {
        Path visits = tempDir.resolve("visits3.csv");
        Files.writeString(visits, "player_id,coin_in\n7,10.0\n7,20.0\n8,5.0\n");
        Path sessions = tempDir.resolve("sessions3.csv");
        Files.writeString(sessions, "player_id,net_win\n7,3.0\n7,4.0\n8,1.0\n8,2.0\n");
        Path players = tempDir.resolve("players3.csv");
        Files.writeString(players, "player_id,tier\n7,gold\n8,silver\n");

        Map<String, Path> csvs = new LinkedHashMap<>();
        csvs.put("visits", visits);
        csvs.put("sessions", sessions);
        csvs.put("players", players);
        try (CsvSource src = CsvSource.of(tempDir.resolve("chain.duckdb"), csvs)) {
            SemanticModel model = SemanticModel.fromProfile(DatasetProfiler.profile(src));
            String sql = QueryPlanner.plan(model, new QueryPlanner.QuerySpec(
                    List.of("visits.coin_in", "sessions.net_win"), List.of("players.tier")));
            List<Map<String, Object>> rows = src.nativeQuery(sql);
            Map<String, Object> gold = rows.stream()
                    .filter(r -> "gold".equals(String.valueOf(r.get("tier")))).findFirst().orElseThrow();
            assertEquals(30.0, ((Number) gold.get("coin_in")).doubleValue(), 0.001);
            assertEquals(7.0, ((Number) gold.get("net_win")).doubleValue(), 0.001);
        }
    }

    @Test
    void rejectsThreeFactTableChains() throws Exception {
        Path a = tempDir.resolve("a4.csv");
        Files.writeString(a, "player_id,x\n1,10\n1,20\n");
        Path b = tempDir.resolve("b4.csv");
        Files.writeString(b, "player_id,y\n1,1\n1,2\n");
        Path c = tempDir.resolve("c4.csv");
        Files.writeString(c, "player_id,z\n1,5\n1,6\n");

        Map<String, Path> csvs = new LinkedHashMap<>();
        csvs.put("a", a);
        csvs.put("b", b);
        csvs.put("c", c);
        try (CsvSource src = CsvSource.of(tempDir.resolve("threefact.duckdb"), csvs)) {
            SemanticModel model = SemanticModel.fromProfile(DatasetProfiler.profile(src));
            assertThrows(UnsupportedOperationException.class, () -> QueryPlanner.plan(model,
                    new QueryPlanner.QuerySpec(List.of("a.x", "b.y", "c.z"), List.of())));
        }
    }
}
