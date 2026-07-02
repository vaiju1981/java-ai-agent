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

class CorrelateTest {

    @TempDir
    Path tempDir;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void correlateRanksMeasuresAgainstTarget() throws Exception {
        Path data = tempDir.resolve("data.csv");
        Files.writeString(data, "x,y,z\n1,2,5\n2,4,3\n3,6,9\n4,8,1\n5,10,7\n");
        Map<String, Path> csvs = new LinkedHashMap<>();
        csvs.put("data", data);
        try (CsvSource src = CsvSource.of(tempDir.resolve("c.duckdb"), csvs)) {
            AnalyticsToolset tools = new AnalyticsToolset(src);
            ToolResult r = tools.correlate().invoke("{\"target\":\"data.x\"}");
            assertFalse(r.error(), r.content());

            JsonNode top = MAPPER.readTree(r.content()).get("correlations").get(0);
            assertEquals("data.y", top.get("column").asText(), "y=2x is the strongest correlate");
            assertTrue(top.get("correlation").asDouble() > 0.99, "perfect positive correlation");
        }
    }
}
