package dev.vaijanath.aiagent.demos.analytics;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AnalyticsToolsetCatalogTest {

    @TempDir
    Path tempDir;

    @Test
    void catalogJsonIncludesDescriptionsWhenSet() throws Exception {
        Map<String, Path> csvs = new LinkedHashMap<>();
        csvs.put("customers", Fixtures.customers(tempDir));
        csvs.put("orders", Fixtures.orders(tempDir));
        try (CsvSource src = CsvSource.of(tempDir.resolve("cat.duckdb"), csvs)) {
            Catalog catalog = Catalog.load(tempDir.resolve("catalog.json"));
            catalog.setColumnDescription("orders", "revenue", "gross booking amount in USD");

            SemanticModel model = SemanticModel.fromProfile(DatasetProfiler.profile(src, catalog));
            AnalyticsToolset tools = new AnalyticsToolset(src, model, catalog);
            String json = tools.catalogJson();
            assertTrue(json.contains("\"orders.revenue -- gross booking amount in USD\""),
                    "description must be attached to the orders.revenue ref: " + json);
        }
    }

    @Test
    void catalogJsonRendersAllColumnsOfACompositeRelationship() throws Exception {
        Map<String, Path> csvs = new LinkedHashMap<>();
        csvs.put("customers", Fixtures.customers(tempDir));
        csvs.put("orders", Fixtures.orders(tempDir));
        try (CsvSource src = CsvSource.of(tempDir.resolve("composite.duckdb"), csvs)) {
            SemanticModel base = SemanticModel.fromProfile(DatasetProfiler.profile(src));
            Relationship composite = new Relationship(
                    "orders", List.of("customer_id", "order_id"), "customers", List.of("customer_id", "zip"),
                    "many_to_one", 1.0);
            SemanticModel model = new SemanticModel(base.metrics(), base.dimensions(),
                    base.entityKeys(), base.timeColumns(), List.of(composite));
            AnalyticsToolset tools = new AnalyticsToolset(src, model);

            String json = tools.catalogJson();
            assertTrue(json.contains("orders.customer_id,order_id ~ customers.customer_id,zip"),
                    "both columns of the composite key must appear on both sides: " + json);
        }
    }
}
