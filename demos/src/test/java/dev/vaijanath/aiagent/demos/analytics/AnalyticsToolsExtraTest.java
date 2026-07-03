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

    @Test
    void compareMergesAdjacentWindowsAndKeepsOneSidedDimensions() throws Exception {
        Path f = tempDir.resolve("periods.csv");
        Files.writeString(f, """
                day,region,amount
                2025-02-10,east,10
                2025-02-15,old,5
                2025-03-10,east,25
                2025-03-20,new,8
                """);
        Map<String, Path> csvs = new LinkedHashMap<>();
        csvs.put("periods", f);
        try (CsvSource src = CsvSource.of(tempDir.resolve("compare.duckdb"), csvs)) {
            AnalyticsToolset tools = new AnalyticsToolset(src);

            ToolResult r = tools.compare().invoke(
                    "{\"metrics\":[\"periods.amount\"],\"dimensions\":[\"periods.region\"],"
                            + "\"timeColumn\":\"periods.day\",\"lastDays\":30}");
            assertFalse(r.error(), r.content());
            JsonNode rows = M.readTree(r.content()).get("rows");
            JsonNode east = rowForRegion(rows, "east").get("metrics").get("periods.amount");
            assertEquals(25.0, east.get("current").asDouble(), 0.001);
            assertEquals(10.0, east.get("previous").asDouble(), 0.001);
            assertEquals(15.0, east.get("absoluteChange").asDouble(), 0.001);
            assertEquals(150.0, east.get("percentChange").asDouble(), 0.001);

            JsonNode newRegion = rowForRegion(rows, "new").get("metrics").get("periods.amount");
            assertEquals(8.0, newRegion.get("current").asDouble(), 0.001);
            assertEquals(0.0, newRegion.get("previous").asDouble(), 0.001);
            assertTrue(newRegion.get("percentChange").isNull(), "division by zero becomes null, not Infinity");

            JsonNode oldRegion = rowForRegion(rows, "old").get("metrics").get("periods.amount");
            assertEquals(0.0, oldRegion.get("current").asDouble(), 0.001);
            assertEquals(5.0, oldRegion.get("previous").asDouble(), 0.001);
        }
    }

    @Test
    void trendComputesLinearDirectionAndNaiveForecast() throws Exception {
        Path f = tempDir.resolve("trend.csv");
        Files.writeString(f, """
                day,amount
                2025-01-01,10
                2025-01-02,20
                2025-01-03,30
                """);
        Map<String, Path> csvs = new LinkedHashMap<>();
        csvs.put("trend", f);
        try (CsvSource src = CsvSource.of(tempDir.resolve("trend.duckdb"), csvs)) {
            AnalyticsToolset tools = new AnalyticsToolset(src);

            ToolResult r = tools.trend().invoke(
                    "{\"metric\":\"trend.amount\",\"timeColumn\":\"trend.day\",\"periodsAhead\":2}");
            assertFalse(r.error(), r.content());
            JsonNode out = M.readTree(r.content());
            assertEquals("increasing", out.get("direction").asText());
            assertEquals(10.0, out.get("slope").asDouble(), 0.001);
            assertTrue(out.get("r2").asDouble() > 0.99);
            assertEquals(40.0, out.get("forecast").get(0).get("value").asDouble(), 0.001);
            assertEquals(50.0, out.get("forecast").get(1).get("value").asDouble(), 0.001);
            assertTrue(out.get("forecast").get(0).get("period").asText().startsWith("2025-01-04"));
        }
    }

    @Test
    void trendLabelsFlatSeries() throws Exception {
        Path f = tempDir.resolve("flat.csv");
        Files.writeString(f, """
                day,amount
                2025-01-01,10
                2025-01-02,10
                2025-01-03,10
                """);
        Map<String, Path> csvs = new LinkedHashMap<>();
        csvs.put("flat", f);
        try (CsvSource src = CsvSource.of(tempDir.resolve("flat.duckdb"), csvs)) {
            AnalyticsToolset tools = new AnalyticsToolset(src);

            ToolResult r = tools.trend().invoke("{\"metric\":\"flat.amount\",\"timeColumn\":\"flat.day\"}");
            assertFalse(r.error(), r.content());
            assertEquals("flat", M.readTree(r.content()).get("direction").asText());
        }
    }

    private static JsonNode rowForRegion(JsonNode rows, String region) {
        for (JsonNode row : rows) {
            if (region.equals(row.get("dimensions").get("periods.region").asText())) {
                return row;
            }
        }
        throw new AssertionError("missing region: " + region + " in " + rows);
    }
}
