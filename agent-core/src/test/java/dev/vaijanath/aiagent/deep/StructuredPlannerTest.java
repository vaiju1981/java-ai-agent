package dev.vaijanath.aiagent.deep;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.vaijanath.aiagent.model.ModelRequest;
import dev.vaijanath.aiagent.model.StructuredOutput;
import java.util.List;
import org.junit.jupiter.api.Test;

class StructuredPlannerTest {

    @Test
    void usesStructuredOutputWithoutParsing() {
        // A structured generator returns a typed Steps directly — no text parsing in the planner.
        StructuredOutput structured = new StructuredOutput() {
            @Override
            public <T> T generate(ModelRequest request, Class<T> type) {
                return type.cast(new LlmPlanner.Steps(List.of("gather sources", "draft", "review")));
            }
        };

        Plan plan = new LlmPlanner(structured).plan("write a report");

        assertEquals(3, plan.steps().size());
        assertEquals("gather sources", plan.steps().get(0).description());
        assertEquals("review", plan.steps().get(2).description());
    }
}
