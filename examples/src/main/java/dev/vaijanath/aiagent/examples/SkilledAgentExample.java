package dev.vaijanath.aiagent.examples;

import dev.vaijanath.aiagent.agent.AgentRequest;
import dev.vaijanath.aiagent.model.ModelPort;
import dev.vaijanath.aiagent.skill.LlmSkillSelector;
import dev.vaijanath.aiagent.skill.Skill;
import dev.vaijanath.aiagent.skill.SkillRegistry;
import dev.vaijanath.aiagent.skill.SkillSelector;
import dev.vaijanath.aiagent.skill.SkillfulAgent;
import java.util.List;

/**
 * Skills doing real work. Two skills are registered; the <b>model</b> chooses which to equip per
 * task (progressive disclosure), and the equipped skill's instructions and tools visibly steer the
 * answer: the math task gets the math tool + a "show your steps" style; the translation task gets a
 * "French only" style. Run with {@code AGENT_MODEL} set.
 */
public final class SkilledAgentExample {

    public static void main(String[] args) {
        ModelPort model = Examples.modelFromEnv();

        SkillRegistry registry = new SkillRegistry()
                .register(Skill.of(
                        "math-tutor",
                        "solve arithmetic problems and teach the steps",
                        "Always call the 'math' tool for any calculation, then explain how you got "
                                + "the result in plain language. Never just state a bare number.",
                        List.of(new MathTool())))
                .register(Skill.of(
                        "french-translator",
                        "translate text into French",
                        "Translate the user's text into French. Output ONLY the French translation."));

        SkillSelector selector = new LlmSkillSelector(model); // the model picks relevant skills
        SkillfulAgent agent = SkillfulAgent.builder()
                .model(model)
                .basePrompt("You are a versatile assistant.")
                .registry(registry)
                .selector(selector)
                .maxSteps(6)
                .build();

        System.out.println("== SkilledAgentExample ==  model: " + model.name());
        System.out.println("(catalog: math-tutor, french-translator)\n");

        ask(agent, "What is 248 divided by 8? Teach me how.");
        ask(agent, "How do you say 'good morning, my friend' in French?");
    }

    private static void ask(SkillfulAgent agent, String task) {
        System.out.println("> " + task);
        System.out.println(agent.run(new AgentRequest(task)).output() + "\n");
    }
}
