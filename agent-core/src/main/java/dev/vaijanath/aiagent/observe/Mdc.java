package dev.vaijanath.aiagent.observe;

import dev.vaijanath.aiagent.annotation.Internal;
import java.util.Map;
import java.util.concurrent.Callable;
import org.slf4j.MDC;

/**
 * Carries the SLF4J {@link MDC} (logging-correlation context — e.g. traceId, sessionId, principal,
 * tenant) from a submitting thread onto a worker thread. The runtime fans tool calls, model retries,
 * and the deadline-bounded turn onto fresh virtual threads, which start with an empty MDC; wrapping the
 * submitted work with this keeps the log lines those threads emit correlated to the turn that spawned
 * them. An edge that populates the MDC (e.g. the Spring starter's {@code AgentTurns}) then flows through
 * the whole runtime.
 */
@Internal
public final class Mdc {

    private Mdc() {}

    /** Wraps {@code task} so it runs with the caller's MDC, restoring the worker's prior MDC afterward. */
    public static <T> Callable<T> propagate(Callable<T> task) {
        Map<String, String> captured = MDC.getCopyOfContextMap();
        return () -> {
            Map<String, String> previous = MDC.getCopyOfContextMap();
            apply(captured);
            try {
                return task.call();
            } finally {
                apply(previous);
            }
        };
    }

    private static void apply(Map<String, String> context) {
        if (context == null) {
            MDC.clear();
        } else {
            MDC.setContextMap(context);
        }
    }
}
