package dev.vaijanath.aiagent.examples;

import dev.vaijanath.aiagent.agent.AgentRequest;
import dev.vaijanath.aiagent.model.ModelPort;
import dev.vaijanath.aiagent.skill.KeywordSkillSelector;
import dev.vaijanath.aiagent.skill.Skill;
import dev.vaijanath.aiagent.skill.SkillRegistry;
import dev.vaijanath.aiagent.skill.SkillSelector;
import dev.vaijanath.aiagent.skill.SkillfulAgent;
import java.util.List;

/**
 * Skills as progressive disclosure: a catalog of skills is registered, the right one is selected per task,
 * and the equipped skill's instructions + tools steer the answer — the math task gets a "show your steps"
 * style and the calculator tool; the translation task gets a "French only" style.
 *
 * <p>Selection uses the deterministic {@link KeywordSkillSelector} (correct offline); swap in
 * {@code LlmSkillSelector} to let the model choose. Set {@code AGENT_MODEL} for real answers.
 */
public final class SkilledAgent {

    private SkilledAgent() {}

    public static void main(String[] args) {
        ModelPort model = Examples.modelFromEnv();
        SkillRegistry registry = new SkillRegistry()
                .register(Skill.of(
                        "math-tutor",
                        "solve arithmetic problems and teach the steps",
                        "Always call the 'math' tool for any calculation, then explain how you got the "
                                + "result in plain language. Never just state a bare number.",
                        List.of(new MathTool())))
                .register(Skill.of(
                        "french-translator",
                        "translate text into French",
                        "Translate the user's text into French. Output ONLY the French translation."));
        SkillSelector selector = new KeywordSkillSelector();
        SkillfulAgent agent = SkillfulAgent.builder()
                .model(model)
                .basePrompt("You are a versatile assistant.")
                .registry(registry)
                .selector(selector)
                .maxSteps(6)
                .build();

        System.out.println("== SkilledAgent ==  model: " + model.name());
        System.out.println("catalog: math-tutor, french-translator\n");
        ask(agent, selector, registry, "What is 248 divided by 8? Teach me how.");
        ask(agent, selector, registry, "How do you say 'good morning, my friend' in French?");
    }

    private static void ask(SkillfulAgent agent, SkillSelector selector, SkillRegistry registry, String task) {
        System.out.println("> " + task);
        System.out.println("  selected: " + selector.select(registry, task).stream().map(Skill::name).toList());
        System.out.println("  " + agent.run(new AgentRequest(task)).output() + "\n");
    }
}
