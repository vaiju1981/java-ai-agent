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

    /**
     * Atomically claim up to {@code estimate} tokens against the budget before a call runs. Returns
     * {@code false} (claiming nothing) if the budget is already spent, so no new work should start.
     * Reserving before the call — and reconciling with {@link #settle} after — bounds how far
     * concurrent in-flight calls can overshoot the ceiling, which a check-then-charge cannot.
     */
    public boolean tryReserve(long estimate) {
        if (estimate < 0) {
            throw new IllegalArgumentException("estimate must be >= 0");
        }
        long current;
        do {
            current = spent.get();
            if (current >= limit) {
                return false;
            }
        } while (!spent.compareAndSet(current, current + estimate));
        return true;
    }

    /** Reconcile a reservation with the actual usage once the call completes. */
    public void settle(long estimate, long actual) {
        long delta = actual - estimate;
        if (delta != 0) {
            spent.addAndGet(delta);
        }
    }

    void add(long tokens) {
        spent.addAndGet(tokens);
    }
}
