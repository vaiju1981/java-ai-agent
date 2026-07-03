package dev.vaijanath.aiagent.demos.analytics;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vaijanath.aiagent.tool.ToolResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RunSqlTest {

    @TempDir
    Path tempDir;

    @Test
    void runsArbitraryReadOnlySqlAndBlocksMutations() throws Exception {
        Path events = tempDir.resolve("events.csv");
        Files.writeString(events, "day,amount\n2025-01-05,10\n2025-01-20,20\n2025-02-10,30\n2025-03-02,40\n");
        Map<String, Path> csvs = new LinkedHashMap<>();
        csvs.put("events", events);
        try (CsvSource src = CsvSource.of(tempDir.resolve("sql.duckdb"), csvs)) {
            AnalyticsToolset tools = new AnalyticsToolset(src);

            // A monthly time-bucket — exactly the kind of span run_query can't express, DuckDB can.
            ToolResult ok = tools.runSql().invoke(
                    "{\"sql\":\"select date_trunc('month', day) as m, sum(amount) total "
                            + "from events group by 1 order by 1\"}");
            assertFalse(ok.error(), ok.content());
            assertTrue(ok.content().contains("total"), "monthly buckets returned");

            // Mutations are refused.
            assertTrue(tools.runSql().invoke("{\"sql\":\"drop table events\"}").error());
            assertTrue(tools.runSql().invoke(
                    "{\"sql\":\"insert into events values ('2025-04-01', 99)\"}").error());
        }
    }
}
