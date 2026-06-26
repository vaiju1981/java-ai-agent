package dev.vaijanath.aiagent.examples;

import dev.vaijanath.aiagent.agent.Agent;
import dev.vaijanath.aiagent.agent.DefaultAgent;
import dev.vaijanath.aiagent.eval.EvalCase;
import dev.vaijanath.aiagent.eval.EvalReport;
import dev.vaijanath.aiagent.eval.Evaluator;
import dev.vaijanath.aiagent.model.ModelPort;
import java.util.List;

/**
 * Evaluation: run an agent against a suite of {@link EvalCase}s and print a pass-rate report — how you
 * catch regressions in a tool-using agent. Each case checks the final answer contains the expected result.
 * Set {@code AGENT_MODEL} so the agent can call the math tool and pass; a stub honestly scores 0.
 */
public final class EvalHarness {

    private EvalHarness() {}

    public static void main(String[] args) {
        ModelPort model = Examples.modelFromEnv();
        Agent agent = DefaultAgent.builder()
                .model(model)
                .systemPrompt("Use the math tool for arithmetic, then state the final number.")
                .tool(new MathTool())
                .maxSteps(6)
                .build();

        EvalReport report = Evaluator.run(agent, List.of(
                EvalCase.contains("add", "What is 12 plus 30? Use the math tool.", "42"),
                EvalCase.contains("multiply", "What is 7 times 8? Use the math tool.", "56"),
                EvalCase.contains("subtract", "What is 100 minus 1? Use the math tool.", "99")));

        System.out.println("== EvalHarness ==  model: " + model.name() + "\n");
        System.out.print(report.render());
        if (Examples.isStub(model)) {
            System.out.println("\n(stub model — set AGENT_MODEL so the agent can use the math tool and pass)");
        }
    }
}
