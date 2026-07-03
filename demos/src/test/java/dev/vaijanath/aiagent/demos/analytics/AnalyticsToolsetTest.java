package dev.vaijanath.aiagent.demos.analytics;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vaijanath.aiagent.tool.ToolResult;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AnalyticsToolsetTest {

    @TempDir
    Path tempDir;

    @Test
    void describeAndRunQueryToolsWorkWithoutAnLlm() throws Exception {
        Map<String, Path> csvs = new LinkedHashMap<>();
        csvs.put("customers", Fixtures.customers(tempDir));
        csvs.put("orders", Fixtures.orders(tempDir));
        try (CsvSource src = CsvSource.of(tempDir.resolve("tools.duckdb"), csvs)) {
            AnalyticsToolset tools = new AnalyticsToolset(src);

            String described = tools.describeDataset().invoke("{}").content();
            assertTrue(described.contains("orders.revenue"), "describe exposes exact metric refs");

            ToolResult result = tools.runQuery().invoke(
                    "{\"metrics\":[\"orders.revenue\"],\"dimensions\":[\"customers.segment\"]}");
            assertFalse(result.error(), "run_query should succeed: " + result.content());
            assertTrue(result.content().contains("enterprise"), "grouped result mentions the segment value");
        }
    }
}
