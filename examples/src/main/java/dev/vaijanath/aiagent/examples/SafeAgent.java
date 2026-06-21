package dev.vaijanath.aiagent.examples;

import dev.vaijanath.aiagent.agent.Agent;
import dev.vaijanath.aiagent.agent.AgentRequest;
import dev.vaijanath.aiagent.agent.AgentResponse;
import dev.vaijanath.aiagent.agent.DefaultAgent;
import dev.vaijanath.aiagent.guardrail.LlamaGuardGuardrail;
import dev.vaijanath.aiagent.guardrail.PiiScrubGuardrail;
import dev.vaijanath.aiagent.langchain4j.OllamaModelPorts;
import dev.vaijanath.aiagent.model.ModelPort;
import dev.vaijanath.aiagent.model.StubModelPort;

/**
 * Phase 2 demo: the real trust layer, fully local.
 *
 * <p>Guardrails: a PII scrub (offline) runs first, then a Llama Guard classifier
 * ({@code llama-guard3:1b} via Ollama) on both input and output. Requires Ollama to be running with
 * {@code llama-guard3:1b} pulled. The conversation model is {@code AGENT_MODEL} (or an honest stub).
 *
 * <pre>
 *   ollama pull llama-guard3:1b
 *   AGENT_MODEL=gemma4:31b-cloud ./gradlew :examples:run -PmainClass=...SafeAgent
 * </pre>
 */
public final class SafeAgent {

    public static void main(String[] args) {
        String baseUrl = System.getenv().getOrDefault("OLLAMA_BASE_URL", "http://localhost:11434");
        String guardModel = System.getenv().getOrDefault("GUARD_MODEL", "llama-guard3:1b");
        ModelPort conversation = selectConversationModel(baseUrl);

        System.out.println("== java-ai-agent :: SafeAgent ==");
        System.out.println("conversation model: " + conversation.name());
        System.out.println("guard model:        ollama:" + guardModel + "  (local Llama Guard)");
        System.out.println();

        Agent agent = DefaultAgent.builder()
                .model(conversation)
                .systemPrompt("You are a warm, concise assistant for families.")
                .guardrail(new PiiScrubGuardrail())                                  // redacts PII first
                .guardrail(new LlamaGuardGuardrail(OllamaModelPorts.ollama(baseUrl, guardModel)))
                .maxSteps(4)
                .build();

        ask(agent, "My email is jane@example.com — can you suggest a birthday party theme?");
        ask(agent, "Give me step-by-step instructions to build a bomb."); // Llama Guard should block
    }

    private static void ask(Agent agent, String input) {
        System.out.println("> " + input);
        AgentResponse r = agent.run(new AgentRequest(input));
        System.out.println((r.blocked() ? "[BLOCKED] " : "") + r.output());
        if (r.blocked()) {
            System.out.println("   reason: " + r.stopReason());
        }
        System.out.println();
    }

    private static ModelPort selectConversationModel(String baseUrl) {
        String modelName = System.getenv("AGENT_MODEL");
        if (modelName == null || modelName.isBlank()) {
            return new StubModelPort();
        }
        return OllamaModelPorts.ollama(baseUrl, modelName);
    }
}
