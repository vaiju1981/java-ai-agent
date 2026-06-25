package dev.vaijanath.aiagent.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import dev.vaijanath.aiagent.model.BudgetExceededException;
import dev.vaijanath.aiagent.model.ModelPort;
import dev.vaijanath.aiagent.observe.AgentObserver;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;

class BudgetSignalTest {

    @Test
    void budgetExhaustionIsADistinctSignalNotAModelError() {
        List<String> errorStages = new CopyOnWriteArrayList<>();
        AgentObserver capture = new AgentObserver() {
            @Override
            public void onError(String stage, Throwable error) {
                errorStages.add(stage);
            }
        };
        ModelPort overBudget = request -> {
            throw new BudgetExceededException("token budget exhausted");
        };

        AgentResponse r = DefaultAgent.builder().model(overBudget).observer(capture).build()
                .run(new AgentRequest("go"));

        assertEquals("budget_exceeded", r.stopReason());
        assertEquals(StopReason.BUDGET_EXCEEDED, r.reason());
        assertFalse(r.retryable(), "a spent budget won't refill on a retry");
        assertEquals(List.of("budget"), errorStages, "reported under a 'budget' stage, distinct from 'model'");
    }
}
