package dev.vaijanath.aiagent.memory;

/**
 * Supplies the conversation {@link Memory} for a session id, so one agent can serve many sessions
 * without their histories interleaving. Implementations must be safe for concurrent calls with
 * different session ids.
 */
public interface ConversationStore {

    /** The memory for {@code sessionId}, created on first use. */
    Memory get(String sessionId);
}
