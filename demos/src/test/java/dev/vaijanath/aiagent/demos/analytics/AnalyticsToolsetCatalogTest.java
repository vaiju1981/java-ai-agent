package dev.vaijanath.aiagent.demos.analytics;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.LinkedHashMap;
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
            assertTrue(json.contains("gross booking amount in USD"), "description must appear in the schema: " + json);
        }
    }
}
