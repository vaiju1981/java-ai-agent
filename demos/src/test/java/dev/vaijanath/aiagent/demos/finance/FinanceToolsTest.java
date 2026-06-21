package dev.vaijanath.aiagent.demos.finance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vaijanath.aiagent.demos.SyntheticData;
import dev.vaijanath.aiagent.tool.Tool;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class FinanceToolsTest {

    private static final List<Tool> TOOLKIT =
            FinanceTools.toolkit("jdbc:sqlite::memory:", Map.of("Dining", 1000.0));

    private static Tool byName(String name) {
        return TOOLKIT.stream().filter(t -> t.name().equals(name)).findFirst().orElseThrow();
    }

    private static Tool dataTool(String db, String name) {
        return FinanceTools.toolkit(db, Map.of("Dining", 1000.0, "Travel", 9000.0)).stream()
                .filter(t -> t.name().equals(name)).findFirst().orElseThrow();
    }

    @Test
    void toolkitHasManyTools() {
        assertTrue(TOOLKIT.size() >= 20, "expected a large toolkit, got " + TOOLKIT.size());
    }

    @Test
    void compoundInterestIsCorrect() {
        // 1000 * 1.1^2 = 1210
        assertEquals("1210.00",
                byName("compound_interest").invoke("{\"principal\":1000,\"annual_rate_pct\":10,\"years\":2}").content());
    }

    @Test
    void tipCalculatorIsCorrect() {
        String out = byName("tip_calculator").invoke("{\"amount\":50,\"percent\":18}").content();
        assertTrue(out.contains("9.00"));
        assertTrue(out.contains("59.00"));
    }

    @Test
    void topMerchantsReturnsTheRequestedNumber() throws Exception {
        String db = SyntheticData.createTransactionsDb(1_000);
        String out = dataTool(db, "top_merchants").invoke("{\"limit\":3}").content();
        assertTrue(out.contains("merchant"), out);
        assertEquals(4, out.strip().split("\n").length, out); // header + 3 rows
    }

    @Test
    void recurringSubscriptionsFindsTheFixedCharges() throws Exception {
        String db = SyntheticData.createTransactionsDb(1_000);
        String out = dataTool(db, "recurring_subscriptions").invoke("{}").content();
        assertTrue(out.contains("Netflix"), out);
        assertTrue(out.contains("15.49"), out);
    }

    @Test
    void categoryBudgetStatusCoversEveryBudget() throws Exception {
        String db = SyntheticData.createTransactionsDb(500);
        String out = dataTool(db, "category_budget_status").invoke("{}").content();
        assertTrue(out.contains("Dining"), out);
        assertTrue(out.contains("Travel"), out);
        assertTrue(out.toLowerCase().contains("budget"), out);
    }
}
