package dev.vaijanath.aiagent.skill;

import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vaijanath.aiagent.agent.Agent;
import dev.vaijanath.aiagent.agent.AgentRequest;
import dev.vaijanath.aiagent.agent.AgentResponse;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

class SkillAcquiringAgentTest {

    @Test
    void registersASkillAfterSucceeding() {
        SkillRegistry registry = new SkillRegistry();
        SkillSynthesizer synthesizer =
                (task, solution) -> Skill.of("learned-skill", "does the thing", "do it like the solution");
        Supplier<Agent> worker = () -> request -> AgentResponse.completed("solved it");

        new SkillAcquiringAgent(worker, registry, synthesizer).run(new AgentRequest("do a thing"));

        assertTrue(registry.get("learned-skill").isPresent(), "a new skill should be acquired");
    }

    @Test
    void doesNotLearnWhenBlocked() {
        SkillRegistry registry = new SkillRegistry();
        SkillSynthesizer synthesizer =
                (task, solution) -> Skill.of("should-not-appear", "x", "y");
        Supplier<Agent> worker = () -> request -> AgentResponse.blocked("nope", "blocked");

        new SkillAcquiringAgent(worker, registry, synthesizer).run(new AgentRequest("bad"));

        assertTrue(registry.get("should-not-appear").isEmpty(), "blocked turns must not teach skills");
    }
}
