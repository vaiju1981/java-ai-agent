package dev.vaijanath.aiagent.demos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
}
