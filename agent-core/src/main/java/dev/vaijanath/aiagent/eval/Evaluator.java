package dev.vaijanath.aiagent.eval;

import dev.vaijanath.aiagent.agent.Agent;
import dev.vaijanath.aiagent.agent.AgentRequest;
import java.util.ArrayList;
import java.util.List;

/** Runs an agent against a set of {@link EvalCase}s and reports how many passed. */
public final class Evaluator {

    private Evaluator() {}

    public static EvalReport run(Agent agent, List<EvalCase> cases) {
        List<EvalResult> results = new ArrayList<>();
        for (EvalCase c : cases) {
            String output;
            try {
                output = agent.run(new AgentRequest(c.input())).output();
            } catch (RuntimeException e) {
                output = "ERROR: " + e;
            }
            results.add(new EvalResult(c.name(), c.passes().test(output), output));
        }
        return new EvalReport(results);
    }
}
