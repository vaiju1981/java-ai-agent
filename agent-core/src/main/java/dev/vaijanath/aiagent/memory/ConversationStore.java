package dev.vaijanath.aiagent.memory;

import java.util.function.Function;

/**
 * Supplies the conversation {@link Memory} for a {@code (tenant, sessionId)} pair. Access is via
 * {@link #withMemory}, which runs an action with the session's memory <em>held</em>: the entry cannot
 * be evicted mid-use, and concurrent calls for the same session serialize on the same memory — so one
 * agent can serve many tenants and sessions without their histories interleaving or splitting.
 * Implementations must be safe for concurrent calls.
 */
public interface ConversationStore {

    /** Runs {@code action} with the {@code (tenant, sessionId)} memory held, and returns its result. */
    <R> R withMemory(String tenant, String sessionId, Function<Memory, R> action);
}
