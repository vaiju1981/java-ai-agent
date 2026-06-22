package dev.vaijanath.aiagent.model;

import java.util.Objects;

/**
 * Enforces a {@link TokenBudget} over any {@link ModelPort}: once the budget is exhausted the next
 * call throws {@link BudgetExceededException} (which a {@code DefaultAgent} turns into a graceful
 * stop).
 *
 * <p>Each call atomically reserves {@code estimatedTokensPerCall} against the budget before running
 * and reconciles to the actual usage afterwards. With a positive estimate this bounds how far
 * concurrent calls can overshoot the ceiling (only {@code limit / estimate} calls can be in flight
 * at once); with the default estimate of {@code 0} it behaves as a soft post-hoc cap that may
 * overshoot by the number of calls in flight when the limit is crossed.
 */
public final class BudgetModelPort implements ModelPort {

    private final ModelPort delegate;
    private final TokenBudget budget;
    private final long estimatedTokensPerCall;

    public BudgetModelPort(ModelPort delegate, TokenBudget budget) {
        this(delegate, budget, 0);
    }

    public BudgetModelPort(ModelPort delegate, TokenBudget budget, long estimatedTokensPerCall) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.budget = Objects.requireNonNull(budget, "budget");
        if (estimatedTokensPerCall < 0) {
            throw new IllegalArgumentException("estimatedTokensPerCall must be >= 0");
        }
        this.estimatedTokensPerCall = estimatedTokensPerCall;
    }

    @Override
    public ModelResponse chat(ModelRequest request) {
        if (!budget.tryReserve(estimatedTokensPerCall)) {
            throw new BudgetExceededException(
                    "token budget exhausted (" + budget.spent() + "/" + budget.limit() + ")");
        }
        long actual = 0;
        try {
            ModelResponse response = delegate.chat(request);
            actual = response.usage().totalTokens();
            return response;
        } finally {
            // Reconcile the reservation with reality (releasing it entirely if the call threw).
            budget.settle(estimatedTokensPerCall, actual);
        }
    }

    @Override
    public String name() {
        return "budget(" + delegate.name() + ")";
    }
}
