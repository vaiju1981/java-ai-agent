package dev.vaijanath.aiagent.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class BudgetModelPortTest {

    private static ModelRequest req() {
        return ModelRequest.of(List.of(Message.user("hi")));
    }

    @Test
    void stopsOnceBudgetExhausted() {
        ModelPort base = request -> ModelResponse.text("hi", new Usage(10, 10)); // 20 tokens/call
        TokenBudget budget = new TokenBudget(15);
        ModelPort budgeted = new BudgetModelPort(base, budget);

        budgeted.chat(req());                    // spends 20 -> now over budget
        assertEquals(20, budget.spent());
        assertThrows(BudgetExceededException.class, () -> budgeted.chat(req()));
    }
}
