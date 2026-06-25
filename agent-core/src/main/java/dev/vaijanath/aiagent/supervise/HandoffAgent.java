package dev.vaijanath.aiagent.supervise;

import dev.vaijanath.aiagent.agent.Agent;
import dev.vaijanath.aiagent.agent.AgentRequest;
import dev.vaijanath.aiagent.agent.AgentResponse;
import dev.vaijanath.aiagent.agent.RequestContext;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Routes a request through a set of peer agents that can <b>hand off</b> control to one another — the
 * Swarm pattern (OpenAI Swarm). A starting agent handles the request; after each hop a {@link Handoff}
 * decides whether a peer should take over, and if so that peer runs next. Unlike {@link SupervisorAgent}
 * (route once, return) there is no central manager — control moves laterally between peers until one
 * keeps it (its output is the answer) or a hop budget is spent.
 *
 * <p>Itself an {@link Agent}, so it composes and can be wrapped once with {@code Trust.govern(...)}.
 * Every hop runs with the request's {@link RequestContext} unchanged, so if the peers share a
 * {@code ConversationStore} they share the conversation (true handoff continuity); otherwise each hop
 * handles the task independently. A {@code blocked} hop (a guardrail stopped it) is returned as-is.
 */
public final class HandoffAgent implements Agent {

    private static final Logger log = LoggerFactory.getLogger(HandoffAgent.class);

    private final Map<String, Agent> agents;
    private final Map<String, String> descriptions;
    private final String start;
    private final Handoff handoff;
    private final int maxHops;

    private HandoffAgent(Builder b) {
        if (b.agents.isEmpty()) {
            throw new IllegalArgumentException("at least one agent is required");
        }
        this.agents = Map.copyOf(b.agents);
        this.descriptions = Map.copyOf(b.descriptions);
        this.handoff = Objects.requireNonNull(b.handoff, "handoff");
        this.start = b.start != null ? b.start : b.firstName;
        if (!agents.containsKey(start)) {
            throw new IllegalArgumentException("start '" + start + "' is not a registered agent");
        }
        this.maxHops = Math.max(1, b.maxHops);
    }

    @Override
    public AgentResponse run(AgentRequest request) {
        String task = request.input();
        RequestContext ctx = request.context();
        String current = start;
        AgentResponse response = null;

        for (int hop = 1; hop <= maxHops; hop++) {
            response = agents.get(current).run(new AgentRequest(task, ctx));
            if (response.blocked()) {
                return response; // a guardrail stopped this hop; surface it unchanged
            }
            String next = handoff.next(task, current, response.output(), descriptions);
            if (next == null || next.isBlank() || next.equals(current) || !agents.containsKey(next)) {
                log.info("handoff: '{}' handled the request after {} hop(s)", current, hop);
                return AgentResponse.completed(response.output());
            }
            log.info("handoff: '{}' -> '{}'", current, next);
            current = next;
        }

        log.info("handoff hit the hop budget ({}); returning the last result", maxHops);
        return AgentResponse.stopped(response == null ? "" : response.output(), "max_hops");
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Fluent builder. At least one {@code agent} and a {@code handoff} are required. */
    public static final class Builder {

        private final Map<String, Agent> agents = new LinkedHashMap<>();
        private final Map<String, String> descriptions = new LinkedHashMap<>();
        private String start;
        private String firstName;
        private Handoff handoff;
        private int maxHops = 5;

        /** Registers a peer; its {@code description} guides the {@link Handoff}'s choices. */
        public Builder agent(String name, String description, Agent agent) {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(agent, "agent");
            if (firstName == null) {
                firstName = name;
            }
            agents.put(name, agent);
            descriptions.put(name, description == null ? "" : description);
            return this;
        }

        /** The agent that handles the request first (default: the first registered). */
        public Builder start(String name) {
            this.start = name;
            return this;
        }

        public Builder handoff(Handoff handoff) {
            this.handoff = handoff;
            return this;
        }

        /** Maximum hops before the loop stops and returns the last result (default 5). */
        public Builder maxHops(int maxHops) {
            this.maxHops = maxHops;
            return this;
        }

        public HandoffAgent build() {
            return new HandoffAgent(this);
        }
    }
}
