package dev.vaijanath.aiagent.model;

/** Thrown by {@link BudgetModelPort} when a {@link TokenBudget} is exhausted. */
public final class BudgetExceededException extends RuntimeException {

    public BudgetExceededException(String message) {
        super(message);
    }
}
