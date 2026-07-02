package dev.vaijanath.aiagent.demos.analytics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SqlSourceTest {

    @TempDir
    Path tempDir;

    @Test
    void profilesAndQueriesALiveSqlDatabase() throws Exception {
        String url = "jdbc:sqlite:" + tempDir.resolve("shop.db");
        try (Connection c = DriverManager.getConnection(url); Statement st = c.createStatement()) {
            st.executeUpdate("create table customers(customer_id integer, segment text, region text)");
            st.executeUpdate("insert into customers values (1,'enterprise','east'),(2,'smb','west'),(3,'enterprise','east')");
            st.executeUpdate("create table orders(order_id integer, customer_id integer, revenue real)");
            st.executeUpdate("insert into orders values (100,1,250.0),(101,1,90.5),(102,2,45.0),(103,3,610.0)");
        }

        try (SqlSource src = SqlSource.of(url)) {
            DatasetProfile profile = DatasetProfiler.profile(src);
            String json = profile.toJson();
            assertTrue(json.contains("IDENTIFIER"), "customer_id -> identifier over JDBC");
            assertTrue(json.contains("MEASURE_ADDITIVE"), "revenue -> measure over JDBC");
            assertTrue(profile.relationships().stream().anyMatch(
                    r -> r.fromColumn().equals("customer_id") && r.toColumn().equals("customer_id")),
                    "orders<->customers discovered over JDBC");

            SemanticModel model = SemanticModel.fromProfile(profile);
            String sql = QueryPlanner.plan(model,
                    new QueryPlanner.QuerySpec(List.of("orders.revenue"), List.of("customers.segment")));
            double enterprise = src.nativeQuery(sql).stream()
                    .filter(r -> "enterprise".equals(String.valueOf(r.get("segment"))))
                    .mapToDouble(r -> ((Number) r.get("revenue")).doubleValue()).sum();
            assertEquals(950.5, enterprise, 0.001);
        }
    }
}
