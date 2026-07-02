package dev.vaijanath.aiagent.demos.analytics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.vaijanath.aiagent.tool.ToolResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AnalyticsToolsExtraTest {

    @TempDir
    Path tempDir;

    private static final ObjectMapper M = new ObjectMapper();

    @Test
    void summarizeAndOutliers() throws Exception {
        Path f = tempDir.resolve("d.csv");
        Files.writeString(f, "val,cat\n1,a\n1,a\n1,b\n1,b\n1,a\n1,b\n1,a\n1,b\n1,a\n100,b\n");
        Map<String, Path> csvs = new LinkedHashMap<>();
        csvs.put("d", f);
        try (CsvSource src = CsvSource.of(tempDir.resolve("x1.duckdb"), csvs)) {
            AnalyticsToolset tools = new AnalyticsToolset(src);

            ToolResult s = tools.summarize().invoke("{\"metric\":\"d.val\"}");
            assertFalse(s.error(), s.content());
            JsonNode summary = M.readTree(s.content()).get("summary");
            assertEquals(10, summary.get("n").asInt());
            assertEquals(100.0, summary.get("max").asDouble(), 0.001);

            ToolResult o = tools.outliers().invoke("{\"metric\":\"d.val\",\"threshold\":2}");
            assertFalse(o.error(), o.content());
            assertTrue(o.content().contains("100"), "the val=100 row is flagged an outlier");
        }
    }

    @Test
    void regressionRanksPredictors() throws Exception {
        Path f = tempDir.resolve("r.csv");
        Files.writeString(f, "a,b\n1,2\n2,4\n3,6\n4,8\n5,10\n");
        Map<String, Path> csvs = new LinkedHashMap<>();
        csvs.put("reg", f);
        try (CsvSource src = CsvSource.of(tempDir.resolve("x2.duckdb"), csvs)) {
            AnalyticsToolset tools = new AnalyticsToolset(src);

            ToolResult r = tools.regression().invoke("{\"target\":\"reg.a\"}");
            assertFalse(r.error(), r.content());
            JsonNode top = M.readTree(r.content()).get("predictors").get(0);
            assertEquals("reg.b", top.get("column").asText(), "b perfectly predicts a");
            assertTrue(top.get("r2").asDouble() > 0.99);
        }
    }
}
