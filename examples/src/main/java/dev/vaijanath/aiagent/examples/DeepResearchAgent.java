package dev.vaijanath.aiagent.examples;

import dev.vaijanath.aiagent.agent.Agent;
import dev.vaijanath.aiagent.agent.AgentRequest;
import dev.vaijanath.aiagent.agent.AgentResponse;
import dev.vaijanath.aiagent.agent.DefaultAgent;
import dev.vaijanath.aiagent.deep.DeepAgent;
import dev.vaijanath.aiagent.deep.LlmPlanner;
import dev.vaijanath.aiagent.langchain4j.OllamaModelPorts;
import dev.vaijanath.aiagent.model.ModelPort;
import dev.vaijanath.aiagent.model.StubModelPort;
import java.util.function.Supplier;

/**
 * Phase 4 demo: a deep agent that plans a task, runs a sub-agent per subtask concurrently
 * (on virtual threads), and synthesizes a final answer.
 *
 * <p>Set {@code AGENT_MODEL} to a pulled Ollama model for real planning/synthesis; otherwise an
 * honest stub is used (which yields a trivial single-step plan).
 */
public final class DeepResearchAgent {

    public static void main(String[] args) {
        ModelPort model = selectModel();
        System.out.println("== java-ai-agent :: DeepResearchAgent ==");
        System.out.println("model: " + model.name());
        System.out.println();

        Supplier<Agent> worker = () -> DefaultAgent.builder()
                .model(model)
                .systemPrompt("Answer the subtask concisely, in 2-3 sentences.")
                .maxSteps(3)
                .build();

        DeepAgent deep = DeepAgent.builder()
                .planner(new LlmPlanner(model, 4))
                .worker(worker)
                .synthesizer(model)
                .build();

        String task = "Give a brief comparison of three popular JVM frameworks for building REST APIs.";
        System.out.println("> " + task + "\n");

        AgentResponse r = deep.run(new AgentRequest(task));

        System.out.println("--- plan ---");
        System.out.println(deep.workspace().read("plan.md").orElse("(none)"));
        System.out.println("--- final answer ---");
        System.out.println(r.output());
    }

    private static ModelPort selectModel() {
        String modelName = System.getenv("AGENT_MODEL");
        if (modelName == null || modelName.isBlank()) {
            return new StubModelPort();
        }
        String baseUrl = System.getenv().getOrDefault("OLLAMA_BASE_URL", "http://localhost:11434");
        return OllamaModelPorts.ollama(baseUrl, modelName);
    }
}
