package dev.vaijanath.aiagent.model;

import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Wraps any {@link ModelPort} with a per-call timeout and bounded retries with jittered linear
 * backoff — so a hung or flaky model degrades predictably instead of stalling an agent forever.
 *
 * <p>Each call runs on a virtual thread; on timeout the caller stops waiting (and cancels the task)
 * rather than blocking on an unresponsive model. Cancellation interrupts the worker, but a blocking
 * client that ignores interrupts only unblocks when its own socket timeout fires — so the underlying
 * adapter <b>must</b> set a client-level read timeout (e.g. {@code OllamaModelPorts} does) for the
 * timeout here to bound resource use, not just caller latency.
 *
 * <p>Retries are for <em>transient</em> failures. A {@link NonRetryableModelException} (e.g. auth or
 * a malformed request — typically a 4xx) fails fast without consuming the remaining attempts; supply
 * a custom predicate to {@link #ResilientModelPort(ModelPort, int, Duration, long, Predicate)} to
 * classify your adapter's exceptions. Backoff is randomized (full jitter) so many agents retrying a
 * recovering provider don't synchronize into a thundering herd.
 */
public final class ResilientModelPort implements ModelPort {

    private static final Logger log = LoggerFactory.getLogger(ResilientModelPort.class);

    /** The default policy: retry anything except an explicitly non-retryable model error. */
    public static final Predicate<Throwable> DEFAULT_RETRYABLE =
            t -> !(t instanceof NonRetryableModelException);

    private final ModelPort delegate;
    private final int maxAttempts;
    private final Duration timeout;
    private final long backoffMillis;
    private final Predicate<Throwable> retryable;

    public ResilientModelPort(ModelPort delegate) {
        this(delegate, 3, Duration.ofSeconds(60), 500);
    }

    public ResilientModelPort(ModelPort delegate, int maxAttempts, Duration timeout, long backoffMillis) {
        this(delegate, maxAttempts, timeout, backoffMillis, DEFAULT_RETRYABLE);
    }

    public ResilientModelPort(ModelPort delegate, int maxAttempts, Duration timeout, long backoffMillis,
            Predicate<Throwable> retryable) {
        this.delegate = delegate;
        this.maxAttempts = Math.max(1, maxAttempts);
        this.timeout = timeout;
        this.backoffMillis = Math.max(0, backoffMillis);
        this.retryable = retryable != null ? retryable : DEFAULT_RETRYABLE;
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
                Throwable cause = e.getCause();
                RuntimeException re = (cause instanceof RuntimeException r) ? r : new RuntimeException(cause);
                if (!retryable.test(cause)) {
                    log.warn("model call failed with a non-retryable error; not retrying: {}", re.toString());
                    throw re;
                }
                last = re;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("interrupted while calling model", e);
            }
            log.warn("model attempt {}/{} failed: {}", attempt, maxAttempts, last.toString());
            if (attempt < maxAttempts) {
                sleep(jitteredBackoff(attempt));
            }
        }
        throw last;
    }

    /** Full jitter over a linearly growing window: a random delay in {@code [0, backoff*attempt]}. */
    private long jitteredBackoff(int attempt) {
        long ceiling = backoffMillis * attempt;
        return ceiling <= 0 ? 0 : ThreadLocalRandom.current().nextLong(ceiling + 1);
    }

    private static void sleep(long millis) {
        if (millis <= 0) {
            return;
        }
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
