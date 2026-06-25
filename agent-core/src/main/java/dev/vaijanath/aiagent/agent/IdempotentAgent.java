package dev.vaijanath.aiagent.agent;

import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Wraps an {@link Agent} so a request carrying an <b>idempotency key</b> returns the prior result on a
 * retry instead of re-running the turn — preventing a retry-after-timeout from re-invoking the model and
 * (more importantly) effectful tools. The key is read from the {@link RequestContext} attribute
 * {@value #KEY_ATTRIBUTE}; a request without one runs normally.
 *
 * <p>The dedup scope is {@code tenant + principal + session + key}. Only <b>non-retryable</b> outcomes
 * are stored ({@link AgentResponse#retryable()}), so a transient failure (e.g. {@code model_error},
 * {@code deadline_exceeded}) still re-runs on retry rather than caching the error.
 *
 * <p>This handles sequential retries (the common case). Two <em>concurrent</em> requests with the same
 * key can still both run, since the check precedes the delegate; a durable store with an atomic reserve
 * is needed to dedup those.
 */
public final class IdempotentAgent implements Agent {

    /** The {@link RequestContext} attribute carrying the client's idempotency key. */
    public static final String KEY_ATTRIBUTE = "idempotencyKey";

    private static final Logger log = LoggerFactory.getLogger(IdempotentAgent.class);

    private final Agent delegate;
    private final IdempotencyStore store;

    public IdempotentAgent(Agent delegate, IdempotencyStore store) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.store = Objects.requireNonNull(store, "store");
    }

    @Override
    public AgentResponse run(AgentRequest request) {
        RequestContext ctx = request.context();
        String key = ctx.attributes().get(KEY_ATTRIBUTE);
        if (key == null || key.isBlank()) {
            return delegate.run(request);
        }
        String scoped = ctx.principal() + " " + ctx.sessionId() + " " + key;

        Optional<AgentResponse> prior = store.lookup(ctx.tenant(), scoped);
        if (prior.isPresent()) {
            log.info("idempotent replay for tenant '{}' — returning the prior result, not re-running",
                    ctx.tenant());
            return prior.get();
        }

        AgentResponse response = delegate.run(request);
        if (!response.retryable()) { // don't cache a transient failure — let a retry actually re-run
            store.save(ctx.tenant(), scoped, response);
        }
        return response;
    }
}
