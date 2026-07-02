package dev.vaijanath.aiagent.demos.analytics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ImportOverrideTest {

    @TempDir
    Path tempDir;

    @Test
    void overrideForcesNumericColumnToText() throws Exception {
        Map<String, Path> csvs = new LinkedHashMap<>();
        csvs.put("customers", Fixtures.customers(tempDir));
        Map<String, Map<String, String>> overrides = Map.of("customers", Map.of("customer_id", "VARCHAR"));

        try (CsvSource src = CsvSource.of(tempDir.resolve("o.duckdb"), csvs, overrides)) {
            String type = String.valueOf(src.nativeQuery(
                    "select data_type from information_schema.columns "
                            + "where table_name='customers' and column_name='customer_id'").get(0).get("data_type"));
            assertTrue(type.toUpperCase().contains("VARCHAR"),
                    "customer_id (auto: BIGINT) must be overridden to VARCHAR, was " + type);

            Object id = src.nativeQuery("select customer_id from customers order by customer_id limit 1")
                    .get(0).get("customer_id");
            assertEquals("1", String.valueOf(id));
        }
    }
}
