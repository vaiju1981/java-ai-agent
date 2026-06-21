package dev.vaijanath.aiagent.deep;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.vaijanath.aiagent.model.ModelPort;
import dev.vaijanath.aiagent.model.ModelResponse;
import org.junit.jupiter.api.Test;

class LlmPlannerTest {

    @Test
    void parsesNumberedAndBulletedLines() {
        ModelPort model = request -> ModelResponse.text("1. Research A\n2) Research B\n- Research C");
        Plan plan = new LlmPlanner(model, 5).plan("task");

        assertEquals(3, plan.steps().size());
        assertEquals("Research A", plan.steps().get(0).description());
        assertEquals("Research B", plan.steps().get(1).description());
        assertEquals("Research C", plan.steps().get(2).description());
    }

    @Test
    void respectsMaxSteps() {
        ModelPort model = request -> ModelResponse.text("1.a\n2.b\n3.c\n4.d\n5.e\n6.f");
        Plan plan = new LlmPlanner(model, 3).plan("t");
        assertEquals(3, plan.steps().size());
    }
}
