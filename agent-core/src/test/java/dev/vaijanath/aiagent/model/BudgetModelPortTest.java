package dev.vaijanath.aiagent.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class BudgetModelPortTest {

    private static ModelRequest req() {
        return ModelRequest.of(List.of(Message.user("hi")));
    }

    @Test
    void stopsOnceBudgetExhausted() {
        ModelPort base = request -> ModelResponse.text("hi", new Usage(10, 10)); // 20 tokens/call
        TokenBudget budget = new TokenBudget(15);
        ModelPort budgeted = new BudgetModelPort(base, budget);

        budgeted.chat(req());                    // spends 20 -> now over budget
        assertEquals(20, budget.spent());
        assertThrows(BudgetExceededException.class, () -> budgeted.chat(req()));
    }

    @Test
    void boundsConcurrentOvershootWithReservation() throws Exception {
        // limit 10, estimate 10 -> the reservation gate admits exactly one call; a check-then-charge
        // budget would let all 32 threads observe spent=0 and overshoot to 320 tokens.
        TokenBudget budget = new TokenBudget(10);
        ModelPort base = request -> ModelResponse.text("ok", new Usage(5, 5)); // 10 tokens
        ModelPort budgeted = new BudgetModelPort(base, budget, 10);

        int threads = 32;
        CyclicBarrier barrier = new CyclicBarrier(threads);
        AtomicInteger ok = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();
        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                futures.add(pool.submit(() -> {
                    try {
                        barrier.await();
                        budgeted.chat(req());
                        ok.incrementAndGet();
                    } catch (BudgetExceededException e) {
                        rejected.incrementAndGet();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }));
            }
            for (Future<?> f : futures) {
                f.get();
            }
        }

        assertEquals(1, ok.get(), "reservation gate must admit exactly one call");
        assertEquals(threads - 1, rejected.get());
        assertEquals(10, budget.spent());
    }
}
