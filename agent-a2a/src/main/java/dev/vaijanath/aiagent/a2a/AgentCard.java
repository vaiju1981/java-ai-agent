package dev.vaijanath.aiagent.a2a;

/**
 * A minimal A2A "agent card": who an agent is, for discovery. Served on {@code GET} by {@link A2aServer}
 * and fetched by {@link RemoteAgent#card()}.
 */
public record AgentCard(String name, String description) {}
