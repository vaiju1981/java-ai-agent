package dev.vaijanath.aiagent.supervise;

import dev.vaijanath.aiagent.agent.Agent;
import dev.vaijanath.aiagent.agent.AgentRequest;
import dev.vaijanath.aiagent.agent.AgentResponse;
import dev.vaijanath.aiagent.agent.RequestContext;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Coordinates specialists in a loop: <b>decide &rarr; delegate &rarr; observe &rarr; decide again</b>,
 * until the {@link Manager} finishes or a round budget is spent. Where {@link SupervisorAgent} routes
 * once and returns, a manager can delegate, inspect the result, and re-delegate — to the same
 * specialist to refine, or to a different one to take the next step.
 *
 * <p>Like every other orchestrator here it is itself an {@link Agent}, so it nests: a specialist may
 * be a {@code DeepAgent}, a {@code ReflectiveAgent}, a {@link SupervisorAgent}, or another
 * {@code ManagerAgent}, and the whole tree can be wrapped once with {@code Trust.govern(...)} so the
 * same guardrails and deadline apply to every node.
 *
 * <p>Each delegation runs in a {@link RequestContext#childSession() child session}: the caller's
 * identity, tenant, trace, and deadline carry through, but specialists never share conversation memory
 * with each other or the parent. The loop holds no state beyond the in-memory transcript of the
 * current turn, so — like {@code DeepAgent} — a crash mid-turn loses in-flight progress; durable,
 * resumable orchestration is a separate concern layered on top.
 */
public final class ManagerAgent implements Agent {

    private static final Logger log = LoggerFactory.getLogger(ManagerAgent.class);

    private final Map<String, Agent> agents;
    private final Map<String, String> descriptions;
    private final Manager manager;
    private final String fallback;
    private final int maxRounds;

    private ManagerAgent(Builder b) {
        if (b.agents.isEmpty()) {
            throw new IllegalArgumentException("at least one specialist is required");
        }
        this.agents = Map.copyOf(b.agents);
        this.descriptions = Map.copyOf(b.descriptions);
        this.manager = Objects.requireNonNull(b.manager, "manager");
        this.fallback = b.fallback != null ? b.fallback : b.firstName;
        if (!agents.containsKey(fallback)) {
            throw new IllegalArgumentException("fallback '" + fallback + "' is not a registered specialist");
        }
        this.maxRounds = Math.max(1, b.maxRounds);
    }

    @Override
    public AgentResponse run(AgentRequest request) {
        String task = request.input();
        RequestContext ctx = request.context();
        List<Manager.Round> history = new ArrayList<>();
        String lastOutput = "";

        for (int round = 1; round <= maxRounds; round++) {
            Manager.Decision decision =
                    Objects.requireNonNull(manager.decide(task, List.copyOf(history), descriptions),
                            "manager returned a null decision");

            if (decision.done()) {
                log.info("manager finished after {} delegation(s)", history.size());
                return AgentResponse.completed(decision.answer() != null ? decision.answer() : lastOutput);
            }

            Agent specialist = agents.get(decision.specialist());
            if (specialist == null) {
                log.warn("manager chose unknown specialist '{}'; using fallback '{}'",
                        decision.specialist(), fallback);
                specialist = agents.get(fallback);
            }
            String instruction = decision.instruction() != null ? decision.instruction() : task;

            AgentResponse resp = specialist.run(new AgentRequest(instruction, ctx.childSession()));
            lastOutput = resp.output();
            history.add(new Manager.Round(decision.specialist(), instruction, resp.output()));
            log.info("round {}/{}: delegated to '{}'", round, maxRounds, decision.specialist());
        }

        log.info("manager hit round budget ({}); returning the last result", maxRounds);
        return AgentResponse.stopped(lastOutput, "max_rounds");
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Fluent builder. At least one {@code specialist} and a {@code manager} are required. */
    public static final class Builder {

        private final Map<String, Agent> agents = new LinkedHashMap<>();
        private final Map<String, String> descriptions = new LinkedHashMap<>();
        private Manager manager;
        private String fallback;
        private String firstName;
        private int maxRounds = 5;

        /** Registers a specialist; its {@code description} guides the {@link Manager}'s choices. */
        public Builder specialist(String name, String description, Agent agent) {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(agent, "agent");
            if (firstName == null) {
                firstName = name;
            }
            agents.put(name, agent);
            descriptions.put(name, description == null ? "" : description);
            return this;
        }

        public Builder manager(Manager manager) {
            this.manager = manager;
            return this;
        }

        /** The specialist to use when the manager names an unknown one (default: the first added). */
        public Builder fallback(String name) {
            this.fallback = name;
            return this;
        }

        /** Maximum delegations before the loop stops and returns the last result (default 5). */
        public Builder maxRounds(int maxRounds) {
            this.maxRounds = maxRounds;
            return this;
        }

        public ManagerAgent build() {
            return new ManagerAgent(this);
        }
    }
}
