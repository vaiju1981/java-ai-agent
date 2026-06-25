package dev.vaijanath.aiagent.a2a;

/**
 * The A2A request body: the {@code input} to run, plus optional identity/session fields that map to the
 * remote turn's {@code RequestContext} (so tenant isolation, tracing, and the deadline survive the
 * network hop). {@code deadlineEpochMillis} carries the caller's remaining deadline so the remote turn
 * is bounded server-side rather than running on after the caller has timed out.
 */
public record A2aRequest(
        String input,
        String sessionId,
        String principal,
        String tenant,
        String traceId,
        Long deadlineEpochMillis) {

    /** A request carrying just the input; identity/session/deadline default on the server. */
    public static A2aRequest of(String input) {
        return new A2aRequest(input, null, null, null, null, null);
    }
}
