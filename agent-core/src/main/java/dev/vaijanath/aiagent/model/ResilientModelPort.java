package dev.vaijanath.aiagent.model;

import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Wraps any {@link ModelPort} with a per-call timeout and bounded retries with linear backoff —
 * so a hung or flaky model degrades predictably instead of stalling an agent forever.
 *
 * <p>Each call runs on a virtual thread; on timeout the caller stops waiting (and cancels the task)
 * rather than blocking on an unresponsive model.
 */
public final class ResilientModelPort implements ModelPort {

    private static final Logger log = LoggerFactory.getLogger(ResilientModelPort.class);

    private final ModelPort delegate;
    private final int maxAttempts;
    private final Duration timeout;
    private final long backoffMillis;

    public ResilientModelPort(ModelPort delegate) {
        this(delegate, 3, Duration.ofSeconds(60), 500);
    }

    public ResilientModelPort(ModelPort delegate, int maxAttempts, Duration timeout, long backoffMillis) {
        this.delegate = delegate;
        this.maxAttempts = Math.max(1, maxAttempts);
        this.timeout = timeout;
        this.backoffMillis = backoffMillis;
    }

    @Override
    public ModelResponse chat(ModelRequest request) {
        RuntimeException last = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            // One fresh virtual thread per attempt — nothing long-lived to leak or shut down.
            FutureTask<ModelResponse> task = new FutureTask<>(() -> delegate.chat(request));
            Thread worker = Thread.ofVirtual().name("resilient-model").start(task);
            try {
                return task.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                task.cancel(true);
                worker.interrupt();
                last = new RuntimeException("model call timed out after " + timeout, e);
            } catch (ExecutionException e) {
                last = (e.getCause() instanceof RuntimeException re) ? re : new RuntimeException(e.getCause());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("interrupted while calling model", e);
            }
            log.warn("model attempt {}/{} failed: {}", attempt, maxAttempts, last.toString());
            if (attempt < maxAttempts) {
                sleep(backoffMillis * attempt);
            }
        }
        throw last;
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public String name() {
        return "resilient(" + delegate.name() + ")";
    }
}
