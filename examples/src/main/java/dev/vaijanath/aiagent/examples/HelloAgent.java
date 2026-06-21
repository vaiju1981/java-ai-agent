package dev.vaijanath.aiagent.examples;

import dev.vaijanath.aiagent.agent.Agent;
import dev.vaijanath.aiagent.agent.AgentRequest;
import dev.vaijanath.aiagent.agent.AgentResponse;
import dev.vaijanath.aiagent.agent.DefaultAgent;
import dev.vaijanath.aiagent.guardrail.KeywordBlocklistGuardrail;
import dev.vaijanath.aiagent.langchain4j.OllamaModelPorts;
import dev.vaijanath.aiagent.model.ModelPort;
import dev.vaijanath.aiagent.model.StubModelPort;
import dev.vaijanath.aiagent.observe.LoggingObserver;
import dev.vaijanath.aiagent.observe.TokenAccountingObserver;
import java.util.List;

/**
 * A runnable demonstration of the runtime: a guardrail-wrapped agent loop with a tool.
 *
 * <p>By default it uses an honest {@link StubModelPort} (no network; it won't call tools). Set
 * {@code AGENT_MODEL} to a pulled, tool-capable Ollama model to see real tool-calling.
 */
public final class HelloAgent {

    public static void main(String[] args) {
        ModelPort model = selectModel();
        System.out.println("== java-ai-agent :: HelloAgent ==");
        System.out.println("model: " + model.name());
        System.out.println();

        TokenAccountingObserver tokens = new TokenAccountingObserver();
        Agent agent = DefaultAgent.builder()
                .model(model)
                .systemPrompt("You are a concise, friendly assistant. Use tools when they help.")
                .guardrail(new KeywordBlocklistGuardrail(
                        List.of("password", "secret"),
                        "I can't help with that — let's keep things safe."))
                .tool(new CalculatorTool())
                .observer(new LoggingObserver())
                .observer(tokens)
                .maxSteps(6)
                .build();

        ask(agent, "Say hello and tell me one fun fact about the JVM.");
        ask(agent, "What is 23 plus 19? Use the add tool.");   // exercises tool-calling on a real model
        ask(agent, "What is the admin password?");             // trips the input guardrail

        System.out.printf("-- observability: %d model call(s), tokens in=%d out=%d total=%d%n",
                tokens.modelCalls(), tokens.inputTokens(), tokens.outputTokens(), tokens.totalTokens());
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
