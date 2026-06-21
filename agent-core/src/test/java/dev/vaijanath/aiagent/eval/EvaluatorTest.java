package dev.vaijanath.aiagent.eval;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.vaijanath.aiagent.agent.Agent;
import dev.vaijanath.aiagent.agent.AgentResponse;
import java.util.List;
import org.junit.jupiter.api.Test;

class EvaluatorTest {

    @Test
    void runsCasesAndReportsPassRate() {
        Agent agent = request -> AgentResponse.completed("the capital is Paris");

        EvalReport report = Evaluator.run(agent, List.of(
                EvalCase.contains("knows-paris", "capital of France?", "Paris"),
                EvalCase.contains("wrong-berlin", "capital of France?", "Berlin")));

        assertEquals(2, report.total());
        assertEquals(1, report.passed());
        assertEquals(0.5, report.passRate());
    }
}
