package dev.vaijanath.aiagent.demos;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AnalyticsMultiTableDatabaseTest {

    @TempDir
    Path tempDir;

    @Test
    void importsMultipleCsvsAndDiscoversRelationships() {
        Map<String, Path> sources = new LinkedHashMap<>();
        sources.put("customers", AnalyticsInsightAgent.sampleCustomersCsv());
        sources.put("orders", AnalyticsInsightAgent.sampleOrdersCsv());

        try (AnalyticsMultiTableDatabase database =
                AnalyticsMultiTableDatabase.importCsvs(tempDir.resolve("multi.sqlite"), sources)) {
            String schema = database.schemaJson();
            String relationships = database.relationshipsJson();
            String segmentRevenue = database.revenueByCustomerSegmentJson();
            String catalog = database.semanticCatalogJson();
            String plan = database.semanticPlanJson(
                    "Show total revenue and average conversion by customer segment and region.");
            String semanticQuery = database.semanticQueryJson(
                    "Show total revenue and average conversion by customer segment and region.");

            assertTrue(schema.contains("\"name\":\"customers\""));
            assertTrue(schema.contains("\"name\":\"orders\""));
            assertTrue(schema.contains("\"customer_id\""));
            assertTrue(relationships.contains("\"fromTable\":\"orders\""));
            assertTrue(relationships.contains("\"fromColumn\":\"customer_id\""));
            assertTrue(relationships.contains("\"toTable\":\"customers\""));
            assertTrue(relationships.contains("\"cardinality\":\"many_to_one\""));
            assertTrue(segmentRevenue.contains("\"segment\":\"enterprise\""));
            assertTrue(segmentRevenue.contains("\"region\":\"east\""));
            assertTrue(segmentRevenue.contains("\"total_revenue\":288945"));
            assertTrue(catalog.contains("\"name\":\"total_revenue\""));
            assertTrue(catalog.contains("\"name\":\"customer_segment\""));
            assertTrue(plan.contains("\"relationship\":\"orders.customer_id -> customers.customer_id\""));
            assertTrue(plan.contains("join"));
            assertTrue(plan.contains("customers"));
            assertTrue(plan.contains("\"customer_segment\""));
            assertTrue(semanticQuery.contains("\"metrics\":[\"total_revenue\",\"avg_conversion_rate\""));
            assertTrue(semanticQuery.contains("\"customer_segment\":\"enterprise\""));
            assertTrue(semanticQuery.contains("\"total_revenue\":288945"));
        }
    }
}
