package dev.vaijanath.aiagent.skill;

import dev.vaijanath.aiagent.agent.Agent;
import dev.vaijanath.aiagent.agent.AgentRequest;
import dev.vaijanath.aiagent.agent.AgentResponse;
import java.util.Objects;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An agent that can <b>propose</b> new skills from experience — but never silently changes its own
 * behavior. After a genuinely successful turn it synthesizes a candidate skill and submits it to a
 * {@link SkillQuarantine} with provenance (source task, author, tenant). The candidate stays pending
 * until a {@link SkillApprover} approves it (default: {@link SkillApprover#manual()}); only then does
 * it enter the active registry. Blocked, errored, and step-exhausted turns teach nothing.
 */
public final class SkillAcquiringAgent implements Agent {

    private static final Logger log = LoggerFactory.getLogger(SkillAcquiringAgent.class);

    private final Supplier<Agent> workerFactory;
    private final SkillQuarantine quarantine;
    private final SkillSynthesizer synthesizer;
    private final SkillApprover approver;

    public SkillAcquiringAgent(
            Supplier<Agent> workerFactory, SkillQuarantine quarantine, SkillSynthesizer synthesizer) {
        this(workerFactory, quarantine, synthesizer, SkillApprover.manual());
    }

    public SkillAcquiringAgent(Supplier<Agent> workerFactory, SkillQuarantine quarantine,
            SkillSynthesizer synthesizer, SkillApprover approver) {
        this.workerFactory = Objects.requireNonNull(workerFactory, "workerFactory");
        this.quarantine = Objects.requireNonNull(quarantine, "quarantine");
        this.synthesizer = Objects.requireNonNull(synthesizer, "synthesizer");
        this.approver = Objects.requireNonNull(approver, "approver");
    }

    @Override
    public AgentResponse run(AgentRequest request) {
        AgentResponse response = workerFactory.get().run(request);
        // Only learn from a genuine success — a blocked, errored, or step-exhausted turn teaches nothing.
        if (response.isCompleted()) {
            String tenant = request.context().tenant();
            Skill learned = synthesizer.synthesize(request.input(), response.output());
            if (learned != null
                    && quarantine.pending(tenant, learned.name()).isEmpty()
                    && quarantine.active(tenant).get(learned.name()).isEmpty()) {
                PendingSkill candidate = quarantine.submit(tenant, learned, request.input(), "model");
                if (approver.approve(candidate)) {
                    quarantine.approve(tenant, learned.name());
                    log.info("acquired and approved skill '{}' (v{}) for tenant '{}'",
                            learned.name(), candidate.provenance().version(), tenant);
                } else {
                    log.info("quarantined skill '{}' for tenant '{}' pending approval",
                            learned.name(), tenant);
                }
            }
        }
        return response;
    }
}
