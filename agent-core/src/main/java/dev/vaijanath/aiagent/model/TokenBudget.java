package dev.vaijanath.aiagent.model;

import java.util.concurrent.atomic.AtomicLong;

/** A shared, thread-safe token budget. Enforced by {@link BudgetModelPort}. */
public final class TokenBudget {

    private final long limit;
    private final AtomicLong spent = new AtomicLong();

    public TokenBudget(long limit) {
        this.limit = limit;
    }

    public long limit() {
        return limit;
    }

    public long spent() {
        return spent.get();
    }

    public long remaining() {
        return Math.max(0, limit - spent.get());
    }

    public boolean exhausted() {
        return spent.get() >= limit;
    }

    void add(long tokens) {
        spent.addAndGet(tokens);
    }
}
