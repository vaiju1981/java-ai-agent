package dev.vaijanath.aiagent.skill;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
        return new SkillQuarantine();
    }

    private static Supplier<Agent> completes() {
        return () -> request -> AgentResponse.completed("solved it");
    }

    @Test
    void successfulTurnQuarantinesButDoesNotActivateByDefault() {
        SkillQuarantine q = quarantine();

        new SkillAcquiringAgent(completes(), q, SYNTH).run(new AgentRequest("do a thing"));

        assertTrue(q.pending("default", "learned-skill").isPresent(), "a candidate should be quarantined");
        assertTrue(q.active().get("learned-skill").isEmpty(), "it must NOT be active without approval");
    }

    @Test
    void approvingAcquisitionActivatesIt() {
        SkillQuarantine q = quarantine();

        new SkillAcquiringAgent(completes(), q, SYNTH, SkillApprover.acceptingAll())
                .run(new AgentRequest("do a thing"));

        assertTrue(q.active().get("learned-skill").isPresent(), "an approved skill should be active");
        assertTrue(q.pending("default", "learned-skill").isEmpty());
    }

    @Test
    void recordsProvenanceWithTenant() {
        SkillQuarantine q = quarantine();
        RequestContext ctx = new RequestContext("s", "bob", "acme", null, null, null);

        new SkillAcquiringAgent(completes(), q, SYNTH).run(new AgentRequest("do a thing", ctx));

        SkillProvenance p = q.pending("acme", "learned-skill").orElseThrow().provenance();
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
    void activeCatalogCannotBeCastBackToTheRegistry() {
        SkillCatalog catalog = quarantine().active();
        assertFalse(catalog instanceof SkillRegistry,
                "the read-only view must not be the mutable registry, or approval can be bypassed");
    }

    @Test
    void rollbackRemovesAFirstVersionSkill() {
        SkillQuarantine q = quarantine();
        q.submit("acme", Skill.of("s", "d", "i"), "task", "model");
        q.approve("acme", "s");
        assertTrue(q.active("acme").get("s").isPresent());

        assertTrue(q.rollback("acme", "s"));
        assertTrue(q.active("acme").get("s").isEmpty(), "rollback removes a v1 skill");
    }

    @Test
    void skillsApprovedForOneTenantAreInvisibleToAnother() {
        SkillQuarantine q = quarantine();
        q.submit("tenant-a", Skill.of("shared-name", "d", "i"), "task", "model");
        q.approve("tenant-a", "shared-name");

        assertTrue(q.active("tenant-a").get("shared-name").isPresent());
        assertTrue(q.active("tenant-b").get("shared-name").isEmpty(), "tenants must not share skills");
    }
}
