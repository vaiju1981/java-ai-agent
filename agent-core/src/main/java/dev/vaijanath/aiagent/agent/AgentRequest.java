package dev.vaijanath.aiagent.agent;

import java.util.Objects;

/**
 * A turn of input to an {@link Agent}, with the {@link RequestContext} that identifies its session,
 * principal, tenant, trace, and deadline.
 */
public record AgentRequest(String input, RequestContext context) {

    public AgentRequest {
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(context, "context");
    }

    /** A one-off turn in its own fresh, isolated session — the safe default. */
    public AgentRequest(String input) {
        this(input, RequestContext.ephemeral());
    }
}
