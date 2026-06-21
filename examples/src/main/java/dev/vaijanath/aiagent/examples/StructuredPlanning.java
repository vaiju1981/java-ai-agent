package dev.vaijanath.aiagent.examples;

import dev.vaijanath.aiagent.deep.LlmPlanner;
import dev.vaijanath.aiagent.deep.Plan;
import dev.vaijanath.aiagent.langchain4j.OllamaModelPorts;
import dev.vaijanath.aiagent.model.StructuredOutput;

/**
 * Structured output in action: the planner asks the model for a JSON object bound straight to a
 * record ({@code LlmPlanner.Steps}) — no text parsing. Needs {@code AGENT_MODEL} (a JSON-capable
 * Ollama model).
 */
public final class StructuredPlanning {

    public static void main(String[] args) {
        String modelName = System.getenv("AGENT_MODEL");
        String baseUrl = System.getenv().getOrDefault("OLLAMA_BASE_URL", "http://localhost:11434");
        String task = "Plan a weekend trip to Tokyo.";

        System.out.println("== StructuredPlanning ==");
        if (modelName == null || modelName.isBlank()) {
            System.out.println("(set AGENT_MODEL to a JSON-capable Ollama model to see structured planning)");
            return;
        }

        StructuredOutput structured = OllamaModelPorts.ollamaStructured(baseUrl, modelName);
        Plan plan = new LlmPlanner(structured, 4).plan(task);

        System.out.println("> " + task);
        System.out.print(plan.render());
    }
}
