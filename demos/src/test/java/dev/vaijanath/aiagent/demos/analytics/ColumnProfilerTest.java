package dev.vaijanath.aiagent.demos.analytics;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ColumnProfilerTest {

    @TempDir
    Path tempDir;

    @Test
    void detectsSignalsFromDataNotColumnNames() throws Exception {
        Map<String, Path> csvs = new LinkedHashMap<>();
        csvs.put("customers", Fixtures.customers(tempDir));
        csvs.put("orders", Fixtures.orders(tempDir));
        try (CsvSource src = CsvSource.of(tempDir.resolve("p.duckdb"), csvs)) {
            ColumnProfile zip = profile(src, "customers", "zip");
            assertTrue(zip.signals().contains("leading-zeros"), "zip 02139 must be flagged leading-zeros");

            ColumnProfile id = profile(src, "customers", "customer_id");
            assertTrue(id.signals().contains("id-like"), "customer_id is unique -> id-like");

            ColumnProfile date = profile(src, "orders", "ordered_at");
            assertTrue(date.signals().contains("date-like"), "2025/01/02 -> date-like");

            ColumnProfile rate = profile(src, "orders", "conversion_rate");
            assertTrue(rate.max() <= 1.0, "conversion_rate is bounded 0..1");
        }
    }

    private ColumnProfile profile(CsvSource src, String table, String column) {
        return src.tables().stream()
                .filter(t -> t.name().equals(table)).findFirst().orElseThrow()
                .columns().stream().filter(c -> c.name().equals(column)).findFirst()
                .map(c -> ColumnProfiler.profile(src, table, c)).orElseThrow();
    }
}
