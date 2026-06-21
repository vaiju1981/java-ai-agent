package dev.vaijanath.aiagent.demos;

import dev.vaijanath.aiagent.agent.Agent;
import dev.vaijanath.aiagent.agent.AgentRequest;
import dev.vaijanath.aiagent.agent.AgentResponse;
import dev.vaijanath.aiagent.agent.DefaultAgent;
import dev.vaijanath.aiagent.deep.DeepAgent;
import dev.vaijanath.aiagent.deep.LlmPlanner;
import dev.vaijanath.aiagent.langchain4j.OllamaModelPorts;
import dev.vaijanath.aiagent.model.ModelPort;
import dev.vaijanath.aiagent.model.StructuredOutput;
import java.util.Map;
import java.util.function.Supplier;

/**
 * A deep agent that writes a decision briefing: it plans the topic into sections (via structured
 * output, so the section list is reliable), writes each section with a concurrent sub-agent, and
 * synthesizes the result — printing the plan, the per-section workspace artifacts, and the final
 * briefing. The deep-agent orchestration is covered by the core tests; this is live-verified.
 * Needs {@code AGENT_MODEL}.
 */
public final class ResearchBriefingDemo {

    public static void main(String[] args) {
        ModelPort model = Demos.modelFromEnv();
        String modelName = System.getenv("AGENT_MODEL");
        String baseUrl = System.getenv().getOrDefault("OLLAMA_BASE_URL", "http://localhost:11434");

        System.out.println("== ResearchBriefingDemo ==  model: " + model.name());
        if (modelName == null || modelName.isBlank()) {
            System.out.println("(set AGENT_MODEL to a model — a deep research briefing needs one)");
            return;
        }
        System.out.println();

        StructuredOutput structured = OllamaModelPorts.ollamaStructured(baseUrl, modelName);
        Supplier<Agent> sectionWriter = () -> DefaultAgent.builder()
                .model(model)
                .systemPrompt("You are a domain expert. Write one focused, concrete paragraph for "
                        + "the given section. Be specific; no fluff.")
                .maxSteps(2)
                .build();

        DeepAgent deep = DeepAgent.builder()
                .planner(new LlmPlanner(structured, 4)) // structured planning → reliable section list
                .worker(sectionWriter)
                .synthesizer(model)
                .build();

        String topic = "Should a startup choose Kotlin or Java for a new backend service in 2026? "
                + "Give a balanced briefing with a recommendation.";
        System.out.println("> " + topic + "\n");

        AgentResponse r = deep.run(new AgentRequest(topic));

        System.out.println("---- PLAN ----");
        System.out.println(deep.workspace().read("plan.md").orElse("(none)"));
        System.out.println("---- SECTION DRAFTS (workspace) ----");
        deep.workspace().files().entrySet().stream()
                .filter(e -> e.getKey().startsWith("step-"))
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> System.out.println("[" + e.getKey() + "]\n" + e.getValue() + "\n"));
        System.out.println("---- BRIEFING ----");
        System.out.println(r.output());
    }
}
