package dev.vaijanath.aiagent.skill;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vaijanath.aiagent.agent.Agent;
import dev.vaijanath.aiagent.agent.AgentRequest;
import dev.vaijanath.aiagent.agent.AgentResponse;
import dev.vaijanath.aiagent.agent.RequestContext;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

class SkillAcquiringAgentTest {

    private static final SkillSynthesizer SYNTH =
            (task, solution) -> Skill.of("learned-skill", "does the thing", "do it like the solution");

    private static SkillQuarantine quarantine() {
        return new SkillQuarantine(new SkillRegistry());
    }

    private static Supplier<Agent> completes() {
        return () -> request -> AgentResponse.completed("solved it");
    }

    @Test
    void successfulTurnQuarantinesButDoesNotActivateByDefault() {
        SkillQuarantine q = quarantine();

        new SkillAcquiringAgent(completes(), q, SYNTH).run(new AgentRequest("do a thing"));

        assertTrue(q.pending("learned-skill").isPresent(), "a candidate should be quarantined");
        assertTrue(q.active().get("learned-skill").isEmpty(), "it must NOT be active without approval");
    }

    @Test
    void approvingAcquisitionActivatesIt() {
        SkillQuarantine q = quarantine();

        new SkillAcquiringAgent(completes(), q, SYNTH, SkillApprover.acceptingAll())
                .run(new AgentRequest("do a thing"));

        assertTrue(q.active().get("learned-skill").isPresent(), "an approved skill should be active");
        assertTrue(q.pending("learned-skill").isEmpty());
    }

    @Test
    void recordsProvenanceWithTenant() {
        SkillQuarantine q = quarantine();
        RequestContext ctx = new RequestContext("s", "bob", "acme", null, null, null);

        new SkillAcquiringAgent(completes(), q, SYNTH).run(new AgentRequest("do a thing", ctx));

        SkillProvenance p = q.pending("learned-skill").orElseThrow().provenance();
        assertEquals("acme", p.tenant());
        assertEquals("model", p.author());
        assertEquals(1, p.version());
    }

    @Test
    void blockedTurnTeachesNothing() {
        SkillQuarantine q = quarantine();
        Supplier<Agent> worker = () -> request -> AgentResponse.blocked("nope", "blocked");

        new SkillAcquiringAgent(worker, q, SYNTH).run(new AgentRequest("bad"));

        assertTrue(q.pending().isEmpty(), "blocked turns must not propose skills");
    }

    @Test
    void stoppedOrErroredTurnTeachesNothing() {
        SkillQuarantine q = quarantine();
        Supplier<Agent> worker = () -> request -> AgentResponse.stopped("partial", "model_error");

        new SkillAcquiringAgent(worker, q, SYNTH).run(new AgentRequest("flaky"));

        assertTrue(q.pending().isEmpty());
    }

    @Test
    void rollbackRemovesAFirstVersionSkill() {
        SkillQuarantine q = quarantine();
        q.submit(Skill.of("s", "d", "i"), "task", "model", "acme");
        q.approve("s");
        assertTrue(q.active().get("s").isPresent());

        assertTrue(q.rollback("s"));
        assertTrue(q.active().get("s").isEmpty(), "rollback removes a v1 skill");
    }
}
