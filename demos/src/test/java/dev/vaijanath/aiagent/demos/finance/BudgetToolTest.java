package dev.vaijanath.aiagent.demos.finance;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vaijanath.aiagent.demos.SyntheticData;
import dev.vaijanath.aiagent.tool.ToolResult;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BudgetToolTest {

    @Test
    void reportsSpendAgainstBudget() throws Exception {
        String db = SyntheticData.createTransactionsDb(500);
        ToolResult r = new BudgetTool(db, Map.of("Dining", 1000.0)).invoke("{\"category\":\"Dining\",\"month\":3}");
        assertFalse(r.error());
        assertTrue(r.content().contains("Dining"));
        assertTrue(r.content().toLowerCase().contains("budget"));
    }

    @Test
    void rejectsUnknownCategory() {
        ToolResult r = new BudgetTool("jdbc:sqlite::memory:", Map.of("Dining", 1000.0))
                .invoke("{\"category\":\"Nope\",\"month\":3}");
        assertTrue(r.error());
    }
}
