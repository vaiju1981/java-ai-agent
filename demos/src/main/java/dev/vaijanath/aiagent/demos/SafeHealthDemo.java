package dev.vaijanath.aiagent.demos;

import dev.vaijanath.aiagent.agent.Agent;
import dev.vaijanath.aiagent.agent.AgentRequest;
import dev.vaijanath.aiagent.agent.AgentResponse;
import dev.vaijanath.aiagent.agent.DefaultAgent;
import dev.vaijanath.aiagent.guardrail.CrisisGuardrail;
import dev.vaijanath.aiagent.guardrail.LlamaGuardGuardrail;
import dev.vaijanath.aiagent.guardrail.PiiScrubGuardrail;
import dev.vaijanath.aiagent.langchain4j.OllamaModelPorts;
import dev.vaijanath.aiagent.model.ModelPort;
import dev.vaijanath.aiagent.model.StubModelPort;

/**
 * A health-literacy assistant on sensitive data — the trust layer in action. It explains lab results
 * in plain language using a curated {@code lab_reference} tool, behind kidguard guardrails
 * (crisis + PII, plus Llama Guard with a model), under a strict "explain, never diagnose" boundary.
 * Needs {@code AGENT_MODEL} for the explanations; the crisis guardrail works regardless.
 */
public final class SafeHealthDemo {

    public static void main(String[] args) {
        ModelPort model = Demos.modelFromEnv();
        boolean real = !(model instanceof StubModelPort);
        String baseUrl = System.getenv().getOrDefault("OLLAMA_BASE_URL", "http://localhost:11434");

        System.out.println("== SafeHealthDemo ==  model: " + model.name());
        System.out.println("guardrails: crisis + PII" + (real ? " + Llama Guard" : "") + "\n");

        DefaultAgent.Builder builder = DefaultAgent.builder()
                .model(model)
                .systemPrompt("You are a careful health-literacy assistant. For any lab value, call "
                        + "the 'lab_reference' tool to get the normal range, then explain in plain "
                        + "language whether the value is typical. NEVER diagnose, never recommend "
                        + "treatment or medication, and always remind the user to discuss results "
                        + "with their doctor.")
                .tool(new LabReferenceTool())
                .guardrail(new CrisisGuardrail())
                .guardrail(new PiiScrubGuardrail())
                .maxSteps(6);
        if (real) {
            builder.guardrail(new LlamaGuardGuardrail(OllamaModelPorts.ollama(baseUrl, "llama-guard3:1b")));
        }
        Agent agent = builder.build();

        ask(agent, "My fasting glucose came back at 110 mg/dL. What does that mean?");
        ask(agent, "Can you explain an LDL cholesterol of 160?");
        // Sensitive input — the crisis guardrail short-circuits with support, before the model.
        ask(agent, "Honestly I feel hopeless and don't want to live anymore.");
    }

    private static void ask(Agent agent, String input) {
        System.out.println("> " + input);
        AgentResponse r = agent.run(new AgentRequest(input));
        System.out.println((r.blocked() ? "[GUARDRAIL] " : "") + r.output() + "\n");
    }
}
