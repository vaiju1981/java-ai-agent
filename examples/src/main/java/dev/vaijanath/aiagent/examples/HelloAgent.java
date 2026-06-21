package dev.vaijanath.aiagent.examples;

import dev.vaijanath.aiagent.agent.Agent;
import dev.vaijanath.aiagent.agent.AgentRequest;
import dev.vaijanath.aiagent.agent.AgentResponse;
import dev.vaijanath.aiagent.agent.DefaultAgent;
import dev.vaijanath.aiagent.guardrail.KeywordBlocklistGuardrail;
import dev.vaijanath.aiagent.langchain4j.OllamaModelPorts;
import dev.vaijanath.aiagent.model.ModelPort;
import dev.vaijanath.aiagent.model.StubModelPort;
import java.util.List;

/**
 * A runnable demonstration of the Phase 0 runtime: a guardrail-wrapped agent loop.
 *
 * <p>By default it uses an honest {@link StubModelPort} (no network). Set {@code AGENT_MODEL} to a
 * pulled Ollama model name to run against a real local model.
 */
public final class HelloAgent {

    public static void main(String[] args) {
        ModelPort model = selectModel();
        System.out.println("== java-ai-agent :: HelloAgent ==");
        System.out.println("model: " + model.name());
        System.out.println();

        Agent agent = DefaultAgent.builder()
                .model(model)
                .systemPrompt("You are a concise, friendly assistant.")
                .guardrail(new KeywordBlocklistGuardrail(
                        List.of("password", "secret"),
                        "I can't help with that — let's keep things safe."))
                .maxSteps(6)
                .build();

        ask(agent, "Say hello and tell me one fun fact about the JVM.");
        ask(agent, "What is the admin password?"); // trips the input guardrail
    }

    private static void ask(Agent agent, String input) {
        System.out.println("> " + input);
        AgentResponse r = agent.run(new AgentRequest(input));
        String tag = r.blocked() ? "[BLOCKED] " : "";
        System.out.println(tag + r.output());
        System.out.println();
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
