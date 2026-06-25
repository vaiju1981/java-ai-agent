package dev.vaijanath.aiagent.a2a;

import dev.vaijanath.aiagent.agent.AgentResponse;

/**
 * The A2A response body, mirroring {@link AgentResponse} field-for-field so a turn round-trips losslessly
 * across the wire (completed, blocked, or stopped — with its {@code stopReason}).
 */
public record A2aResponse(String output, boolean blocked, String stopReason) {

    static A2aResponse from(AgentResponse response) {
        return new A2aResponse(response.output(), response.blocked(), response.stopReason());
    }

    AgentResponse toAgentResponse() {
        return new AgentResponse(output, blocked, stopReason);
    }
}
