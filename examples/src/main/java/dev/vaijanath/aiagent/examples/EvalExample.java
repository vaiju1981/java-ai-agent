package dev.vaijanath.aiagent.examples;

import dev.vaijanath.aiagent.agent.Agent;
import dev.vaijanath.aiagent.agent.DefaultAgent;
import dev.vaijanath.aiagent.eval.EvalCase;
import dev.vaijanath.aiagent.eval.EvalReport;
import dev.vaijanath.aiagent.eval.Evaluator;
import dev.vaijanath.aiagent.model.ModelPort;
import java.util.List;

/**
 * Evaluates an agent against a small suite and prints a pass-rate report. Run with
 * {@code AGENT_MODEL} set — the agent must call the math tool and reach the right number.
 */
public final class EvalExample {

    public static void main(String[] args) {
        ModelPort model = Examples.modelFromEnv();
        Agent agent = DefaultAgent.builder()
                .model(model)
                .systemPrompt("Use the math tool for arithmetic, then state the final number.")
                .tool(new MathTool())
                .maxSteps(6)
                .build();

        EvalReport report = Evaluator.run(agent, List.of(
                EvalCase.contains("12+30", "What is 12 plus 30? Use the math tool.", "42"),
                EvalCase.contains("7x8", "What is 7 times 8? Use the math tool.", "56"),
                EvalCase.contains("100-1", "What is 100 minus 1? Use the math tool.", "99")));

        System.out.println("== EvalExample ==  model: " + model.name() + "\n");
        System.out.print(report.render());
    }
}
