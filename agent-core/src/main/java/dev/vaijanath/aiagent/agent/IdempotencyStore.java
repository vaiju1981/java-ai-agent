package dev.vaijanath.aiagent.agent;

import java.util.Optional;

/**
 * Remembers the result of a turn so a retried request carrying the same idempotency key returns the
 * prior result instead of re-running the turn — and, crucially, its effectful tools. Used by
 * {@link IdempotentAgent}. Implementations are keyed per {@code tenant}; the rest of the dedup scope
 * (principal, session, client key) is folded into {@code key} by the caller.
 *
 * <p>An in-memory implementation ({@link InMemoryIdempotencyStore}) suits a single instance; back it
 * with a durable, shared store for restart- and multi-replica-safe deduplication.
 */
public interface IdempotencyStore {

    /** The stored result for {@code (tenant, key)}, if a matching turn already completed. */
    Optional<AgentResponse> lookup(String tenant, String key);

    /** Records {@code response} as the result for {@code (tenant, key)}. First write wins. */
    void save(String tenant, String key, AgentResponse response);
}
