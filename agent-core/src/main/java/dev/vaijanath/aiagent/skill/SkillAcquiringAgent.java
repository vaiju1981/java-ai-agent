package dev.vaijanath.aiagent.skill;

import dev.vaijanath.aiagent.agent.Agent;
import dev.vaijanath.aiagent.agent.AgentRequest;
import dev.vaijanath.aiagent.agent.AgentResponse;
import java.util.Objects;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An agent that <b>evolves</b>: after successfully handling a task it distills a reusable
 * {@link Skill} and registers it, so its capabilities grow from experience (a lightweight,
 * inspectable form of self-improvement — every acquired skill is an explicit registry entry).
 */
public final class SkillAcquiringAgent implements Agent {

    private static final Logger log = LoggerFactory.getLogger(SkillAcquiringAgent.class);

    private final Supplier<Agent> workerFactory;
    private final SkillRegistry registry;
    private final SkillSynthesizer synthesizer;

    public SkillAcquiringAgent(Supplier<Agent> workerFactory, SkillRegistry registry,
            SkillSynthesizer synthesizer) {
        this.workerFactory = Objects.requireNonNull(workerFactory, "workerFactory");
        this.registry = Objects.requireNonNull(registry, "registry");
        this.synthesizer = Objects.requireNonNull(synthesizer, "synthesizer");
    }

    @Override
    public AgentResponse run(AgentRequest request) {
        AgentResponse response = workerFactory.get().run(request);
        // Only learn from a genuine success — a blocked, errored, or step-exhausted turn teaches nothing.
        if (response.isCompleted()) {
            Skill learned = synthesizer.synthesize(request.input(), response.output());
            if (learned != null && registry.get(learned.name()).isEmpty()) {
                registry.register(learned);
                log.info("acquired new skill: '{}'", learned.name());
            }
        }
        return response;
    }
}
