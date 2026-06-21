package dev.vaijanath.aiagent.examples;

import dev.vaijanath.aiagent.agent.Agent;
import dev.vaijanath.aiagent.agent.AgentRequest;
import dev.vaijanath.aiagent.agent.AgentResponse;
import dev.vaijanath.aiagent.agent.DefaultAgent;
import dev.vaijanath.aiagent.deep.DeepAgent;
import dev.vaijanath.aiagent.deep.LlmPlanner;
import dev.vaijanath.aiagent.model.ModelPort;
import java.util.Map;
import java.util.function.Supplier;

/**
 * A richer deep agent: it plans a briefing into sections, writes each section with a separate
 * sub-agent (concurrently, on virtual threads), and synthesizes the final document. It prints the
 * generated plan, the per-section drafts saved in the {@code Workspace}, and the final briefing — so
 * you can see the intermediate artifacts, not just the answer. Run with {@code AGENT_MODEL}.
 */
public final class ResearchAssistant {

    public static void main(String[] args) {
        ModelPort model = Examples.modelFromEnv();

        Supplier<Agent> sectionWriter = () -> DefaultAgent.builder()
                .model(model)
                .systemPrompt("You are a domain expert. Answer the subtask in one focused, concrete "
                        + "paragraph. Be specific; no fluff.")
                .maxSteps(2)
                .build();

        DeepAgent deep = DeepAgent.builder()
                .planner(new LlmPlanner(model, 4))
                .worker(sectionWriter)
                .synthesizer(model)
                .build();

        String task = "Write a short CTO briefing: should a privacy-sensitive startup run LLM "
                + "inference on-device or in the cloud? Cover the key tradeoffs and give a recommendation.";

        System.out.println("== ResearchAssistant ==  model: " + model.name() + "\n");
        System.out.println("> " + task + "\n");

        AgentResponse r = deep.run(new AgentRequest(task));

        System.out.println("---- PLAN ----");
        System.out.println(deep.workspace().read("plan.md").orElse("(none)"));
        System.out.println("---- SECTION DRAFTS (workspace artifacts) ----");
        deep.workspace().files().entrySet().stream()
                .filter(e -> e.getKey().startsWith("step-"))
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> System.out.println("[" + e.getKey() + "]\n" + e.getValue() + "\n"));
        System.out.println("---- FINAL BRIEFING ----");
        System.out.println(r.output());
    }
}
