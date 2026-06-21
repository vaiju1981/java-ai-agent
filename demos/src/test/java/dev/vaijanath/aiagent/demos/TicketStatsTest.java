package dev.vaijanath.aiagent.demos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vaijanath.aiagent.demos.TicketTriager.TriageResult;
import java.util.List;
import org.junit.jupiter.api.Test;

class TicketStatsTest {

    private static final List<TriageResult> RESULTS = List.of(
            new TriageResult("high", "Bug", "Engineering"),
            new TriageResult("low", "Bug", "Engineering"),
            new TriageResult("urgent", "Billing", "Payments"));

    @Test
    void countsByCategory() {
        assertEquals(2L, TicketStats.countBy(RESULTS, TriageResult::category).get("Bug"));
        assertEquals(1L, TicketStats.countBy(RESULTS, TriageResult::category).get("Billing"));
    }

    @Test
    void summaryMentionsCategoriesAndPriorities() {
        String s = TicketStats.summary(RESULTS);
        assertTrue(s.contains("Bug"));
        assertTrue(s.contains("urgent"));
    }
}
