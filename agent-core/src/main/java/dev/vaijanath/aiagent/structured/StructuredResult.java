package dev.vaijanath.aiagent.structured;

import dev.vaijanath.aiagent.agent.AgentResponse;

/**
 * The outcome of a {@link StructuredAgent} turn: the typed {@code value} (when the turn completed and
 * was coerced) together with the underlying {@link AgentResponse} — so callers keep the full text,
 * stop reason, and audit trail, not just the parsed object.
 *
 * <p>{@code value} is {@code null} when the turn did not genuinely complete — e.g. a guardrail blocked
 * it or it hit max steps — in which case {@code raw} explains why and no coercion was attempted (a
 * guardrail's safe replacement is never force-fit into your schema). Use {@link #present()} to branch,
 * or {@link #orElseThrow()} when a value is required.
 *
 * @param value the coerced result, or {@code null} if the turn did not complete
 * @param raw   the underlying agent response (always present)
 */
public record StructuredResult<T>(T value, AgentResponse raw) {

    /** True when a typed value was produced (the turn completed and coercion succeeded). */
    public boolean present() {
        return value != null;
    }

    /** The value, or an {@link IllegalStateException} explaining why the turn produced none. */
    public T orElseThrow() {
        if (value == null) {
            throw new IllegalStateException("no structured value: turn ended with stopReason="
                    + raw.stopReason() + (raw.blocked() ? " (blocked)" : ""));
        }
        return value;
    }
}
