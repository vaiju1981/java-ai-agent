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
 * Rung 4 — skills with progressive disclosure. Two skills are registered; per task the agent equips
 * only the relevant one (its instructions enter the prompt and its tools are registered). Watch the
 * "equipped …" line differ between the two tasks.
 */
public final class SkilledAgentExample {

    public static void main(String[] args) {
        ModelPort model = Examples.modelFromEnv();

        SkillRegistry registry = new SkillRegistry()
                .register(Skill.of(
                        "calculator",
                        "calculate arithmetic and add numbers",
                        "When asked to add numbers, call the add tool and report its result.",
                        List.of(new CalculatorTool())))
                .register(Skill.of(
                        "poet",
                        "write short poems and haiku",
                        "Write vivid, concise poetry. A haiku has three lines of 5, 7, 5 syllables."));

        SkillSelector selector = new KeywordSkillSelector();
        SkilledRun run = new SkilledRun(model, registry, selector);

        run.ask("Please calculate 12 plus 30 using the add tool.");
        run.ask("Write a haiku poem about the JVM.");
    }

    private record SkilledRun(ModelPort model, SkillRegistry registry, SkillSelector selector) {

        void ask(String task) {
            System.out.println("> " + task);
            System.out.println("  equipped: "
                    + selector.select(registry, task).stream().map(Skill::name).toList());
            String out = SkillfulAgent.builder()
                    .model(model)
                    .basePrompt("You are a versatile assistant.")
                    .registry(registry)
                    .selector(selector)
                    .build()
                    .run(new AgentRequest(task))
                    .output();
            System.out.println("  " + out + "\n");
        }
    }
}
