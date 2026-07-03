package dev.vaijanath.aiagent.demos.analytics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CsvSourceTest {

    @TempDir
    Path tempDir;

    @Test
    void importsMultipleCsvsAndQueriesAcrossThem() throws Exception {
        Map<String, Path> csvs = new LinkedHashMap<>();
        csvs.put("customers", Fixtures.customers(tempDir));
        csvs.put("orders", Fixtures.orders(tempDir));

        try (CsvSource source = CsvSource.of(tempDir.resolve("m1.duckdb"), csvs)) {
            List<TableSchema> tables = source.tables();
            assertEquals(2, tables.size());
            assertTrue(tables.stream().anyMatch(t -> t.name().equals("customers") && t.rows() == 3));

            List<Map<String, Object>> sample = source.sample("orders", 2);
            assertEquals(2, sample.size());
            assertTrue(sample.get(0).containsKey("customer_id"));

            List<Map<String, Object>> joined = source.nativeQuery(
                    "select c.segment, count(*) n from orders o "
                            + "join customers c on o.customer_id = c.customer_id group by 1 order by 1");
            assertTrue(joined.stream().anyMatch(r -> "enterprise".equals(String.valueOf(r.get("segment")))));
        }
    }

    @Test
    void locksDownExternalFileAccessAfterImport() throws Exception {
        Map<String, Path> csvs = Map.of("customers", Fixtures.customers(tempDir));
        try (CsvSource source = CsvSource.of(tempDir.resolve("m2.duckdb"), csvs)) {
            assertThrows(RuntimeException.class,
                    () -> source.nativeQuery("select * from read_csv_auto('" + Fixtures.orders(tempDir) + "')"));
        }
    }
}
