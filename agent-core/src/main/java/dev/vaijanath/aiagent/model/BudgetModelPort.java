package dev.vaijanath.aiagent.model;

import java.util.Objects;

/**
 * Enforces a {@link TokenBudget} over any {@link ModelPort}: once the budget is exhausted the next
 * call throws {@link BudgetExceededException} (which a {@code DefaultAgent} turns into a graceful
 * stop). Usage is charged after each successful call, so a single call may overshoot slightly.
 */
public final class BudgetModelPort implements ModelPort {

    private final ModelPort delegate;
    private final TokenBudget budget;

    public BudgetModelPort(ModelPort delegate, TokenBudget budget) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.budget = Objects.requireNonNull(budget, "budget");
    }

    @Override
    public ModelResponse chat(ModelRequest request) {
        if (budget.exhausted()) {
            throw new BudgetExceededException(
                    "token budget exhausted (" + budget.spent() + "/" + budget.limit() + ")");
        }
        ModelResponse response = delegate.chat(request);
        budget.add(response.usage().totalTokens());
        return response;
    }

    @Override
    public String name() {
        return "budget(" + delegate.name() + ")";
    }
}
