package dev.vaijanath.aiagent.demos.logs;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vaijanath.aiagent.demos.SqlTool;
import dev.vaijanath.aiagent.tool.ToolResult;
import org.junit.jupiter.api.Test;

class SyntheticLogsTest {

    @Test
    void generatesTheRequestedNumberOfRows() throws Exception {
        String db = SyntheticLogs.createLogsDb(2_000);
        assertEquals(2_000, SyntheticLogs.count(db, "logs"));
    }

    @Test
    void isQueryableViaTheSqlTool() throws Exception {
        String db = SyntheticLogs.createLogsDb(2_000);
        ToolResult r = new SqlTool(db, 50).invoke(
                "{\"query\":\"SELECT level, COUNT(*) AS n FROM logs GROUP BY level\"}");
        assertTrue(r.content().contains("INFO"));
    }
}
