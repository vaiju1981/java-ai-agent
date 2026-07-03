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
        Files.writeString(visits, "customer_id,spend\n7,10.0\n7,20.0\n8,5.0\n");
        Path sessions = tempDir.resolve("sessions3.csv");
        Files.writeString(sessions, "customer_id,revenue\n7,3.0\n7,4.0\n8,1.0\n8,2.0\n");
        Path customers = tempDir.resolve("customers3.csv");
        Files.writeString(customers, "customer_id,tier\n7,gold\n8,silver\n");

        Map<String, Path> csvs = new LinkedHashMap<>();
        csvs.put("visits", visits);
        csvs.put("sessions", sessions);
        csvs.put("customers", customers);
        try (CsvSource src = CsvSource.of(tempDir.resolve("chain.duckdb"), csvs)) {
            SemanticModel model = SemanticModel.fromProfile(DatasetProfiler.profile(src));
            String sql = QueryPlanner.plan(model, new QueryPlanner.QuerySpec(
                    List.of("visits.spend", "sessions.revenue"), List.of("customers.tier")));
            List<Map<String, Object>> rows = src.nativeQuery(sql);
            Map<String, Object> gold = rows.stream()
                    .filter(r -> "gold".equals(String.valueOf(r.get("tier")))).findFirst().orElseThrow();
            assertEquals(30.0, ((Number) gold.get("spend")).doubleValue(), 0.001);
            assertEquals(7.0, ((Number) gold.get("revenue")).doubleValue(), 0.001);
        }
    }

    @Test
    void rejectsTwoFactsConnectedOnlyThroughABridgeDimension() {
        // visits and sessions share no column and no direct relationship; each relates to customers
        // via a different key (customer_id / session_key). factChainJoin requires a direct fact-to-fact
        // shared_key edge, so BFS routing both facts through the customers dimension must be rejected
        // rather than silently joining them through it.
        SemanticModel.Metric visitsSpend = new SemanticModel.Metric("visits", "spend", "sum");
        SemanticModel.Metric sessionsRevenue = new SemanticModel.Metric("sessions", "revenue", "sum");
        Relationship visitsToCustomers = new Relationship(
                "visits", List.of("customer_id"), "customers", List.of("customer_id"), "many_to_one", 1.0);
        Relationship sessionsToCustomers = new Relationship(
                "sessions", List.of("session_key"), "customers", List.of("session_key"), "many_to_one", 1.0);
        SemanticModel model = new SemanticModel(
                List.of(visitsSpend, sessionsRevenue), List.of(), List.of(), List.of(),
                List.of(visitsToCustomers, sessionsToCustomers));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> QueryPlanner.plan(model,
                new QueryPlanner.QuerySpec(List.of("visits.spend", "sessions.revenue"), List.of())));
        assertEquals("fact tables are not directly connected by a shared key", ex.getMessage());
    }

    @Test
    void rejectsThreeFactTableChains() throws Exception {
        Path a = tempDir.resolve("a4.csv");
        Files.writeString(a, "customer_id,x\n1,10\n1,20\n");
        Path b = tempDir.resolve("b4.csv");
        Files.writeString(b, "customer_id,y\n1,1\n1,2\n");
        Path c = tempDir.resolve("c4.csv");
        Files.writeString(c, "customer_id,z\n1,5\n1,6\n");

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
