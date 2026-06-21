package dev.vaijanath.aiagent.memory;

/**
 * Supplies the conversation {@link Memory} for a {@code (tenant, sessionId)} pair, so one agent can
 * serve many tenants and sessions without their histories interleaving — two tenants reusing the same
 * session id never share memory. Implementations must be safe for concurrent calls.
 */
public interface ConversationStore {

    /** The memory for {@code (tenant, sessionId)}, created on first use. */
    Memory get(String tenant, String sessionId);
}
