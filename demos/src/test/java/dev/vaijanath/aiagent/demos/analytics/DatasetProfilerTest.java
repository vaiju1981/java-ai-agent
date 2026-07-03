package dev.vaijanath.aiagent.demos.analytics;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DatasetProfilerTest {

    @TempDir
    Path tempDir;

    @Test
    void profilesArbitraryMultiTableCsvWithoutHardcoding() throws Exception {
        Map<String, Path> csvs = new LinkedHashMap<>();
        csvs.put("customers", Fixtures.customers(tempDir));
        csvs.put("orders", Fixtures.orders(tempDir));
        try (CsvSource src = CsvSource.of(tempDir.resolve("d.duckdb"), csvs)) {
            DatasetProfile profile = DatasetProfiler.profile(src);
            String json = profile.toJson();

            assertTrue(json.contains("\"MEASURE_ADDITIVE\""), "revenue should be an additive measure");
            assertTrue(json.contains("\"IDENTIFIER\""), "customer_id should be an identifier");
            assertTrue(json.contains("\"DATE\""), "ordered_at should be a date");
            assertTrue(profile.relationships().stream().anyMatch(r ->
                    r.fromColumn().equals("customer_id") && r.toColumn().equals("customer_id")),
                    "orders<->customers linked on customer_id");
            assertTrue(profile.importPlan().getOrDefault("customers", Map.of()).containsKey("zip"),
                    "zip flagged for VARCHAR override (leading zeros)");
        }
    }
}
