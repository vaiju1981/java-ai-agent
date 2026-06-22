package dev.vaijanath.aiagent.model;

/**
 * Thrown by {@link BudgetModelPort} when a {@link TokenBudget} is exhausted. It is non-retryable —
 * retrying cannot create budget — so {@link ResilientModelPort} fails fast instead of burning its
 * attempts, and a {@code DefaultAgent} turns it into a graceful stop.
 */
public final class BudgetExceededException extends NonRetryableModelException {

    public BudgetExceededException(String message) {
        super(message);
    }
}
