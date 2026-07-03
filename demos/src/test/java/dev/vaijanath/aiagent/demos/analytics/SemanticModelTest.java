package dev.vaijanath.aiagent.demos.analytics;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SemanticModelTest {

    @TempDir
    Path tempDir;

    @Test
    void generatesMetricsAndDimensionsFromProfile() throws Exception {
        Map<String, Path> csvs = new LinkedHashMap<>();
        csvs.put("customers", Fixtures.customers(tempDir));
        csvs.put("orders", Fixtures.orders(tempDir));
        try (CsvSource src = CsvSource.of(tempDir.resolve("sm.duckdb"), csvs)) {
            SemanticModel model = SemanticModel.fromProfile(DatasetProfiler.profile(src));

            assertTrue(model.metrics().stream().anyMatch(
                    m -> m.column().equals("revenue") && m.aggregation().equals("sum")),
                    "revenue -> sum metric");
            assertTrue(model.metrics().stream().anyMatch(
                    m -> m.column().equals("conversion_rate") && m.aggregation().equals("avg")),
                    "conversion_rate -> avg metric");
            assertTrue(model.dimensions().stream().anyMatch(d -> d.column().equals("segment")),
                    "segment -> dimension");
            assertTrue(model.entityKeys().stream().anyMatch(k -> k.endsWith(".customer_id")),
                    "customer_id -> entity key");
            assertTrue(model.relationships().stream().anyMatch(r -> r.fromColumn().equals("customer_id")),
                    "customer_id relationship carried over");
        }
    }
}
