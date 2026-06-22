package dev.vaijanath.aiagent.supervise;

import dev.vaijanath.aiagent.agent.Agent;
import dev.vaijanath.aiagent.agent.AgentRequest;
import dev.vaijanath.aiagent.agent.AgentResponse;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Routes each request to one of several named specialist agents — the supervisor / handoff pattern.
 * A {@link Router} picks the best specialist by name and the supervisor delegates the request to it.
 * If the router returns an unknown name, the request goes to the fallback specialist (the first one
 * registered, unless set otherwise), so routing is always resolved to a real agent.
 *
 * <p>The supervisor is itself an {@link Agent}, so it composes: a specialist can be another
 * supervisor, a {@code DeepAgent}, or any wrapped agent.
 */
public final class SupervisorAgent implements Agent {

    private final Map<String, Agent> agents;
    private final Map<String, String> descriptions;
    private final Router router;
    private final String fallback;

    private SupervisorAgent(Builder builder) {
        if (builder.agents.isEmpty()) {
            throw new IllegalArgumentException("at least one specialist is required");
        }
        this.agents = Map.copyOf(builder.agents);
        this.descriptions = Map.copyOf(builder.descriptions);
        this.router = Objects.requireNonNull(builder.router, "router");
        this.fallback = builder.fallback != null ? builder.fallback : builder.firstName;
        if (!agents.containsKey(fallback)) {
            throw new IllegalArgumentException("fallback '" + fallback + "' is not a registered specialist");
        }
    }

    @Override
    public AgentResponse run(AgentRequest request) {
        String chosen = router.route(request.input(), descriptions);
        Agent agent = agents.getOrDefault(chosen, agents.get(fallback));
        return agent.run(request);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private final Map<String, Agent> agents = new LinkedHashMap<>();
        private final Map<String, String> descriptions = new LinkedHashMap<>();
        private Router router;
        private String fallback;
        private String firstName;

        /** Registers a specialist; its {@code description} guides the {@link Router}. */
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

        public Builder router(Router router) {
            this.router = router;
            return this;
        }

        /** The specialist to use when routing fails to name a known one (default: the first added). */
        public Builder fallback(String name) {
            this.fallback = name;
            return this;
        }

        public SupervisorAgent build() {
            return new SupervisorAgent(this);
        }
    }
}
