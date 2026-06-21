package dev.vaijanath.aiagent.agent;

import java.util.Objects;

/** A turn of input to an {@link Agent}. */
public record AgentRequest(String input) {

    public AgentRequest {
        Objects.requireNonNull(input, "input");
    }
}
