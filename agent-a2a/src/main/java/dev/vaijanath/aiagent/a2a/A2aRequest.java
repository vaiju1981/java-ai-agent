package dev.vaijanath.aiagent.a2a;

/**
 * The A2A request body: the {@code input} to run, plus optional identity/session fields that map to the
 * remote turn's {@code RequestContext} (so tenant isolation and tracing survive the network hop).
 */
public record A2aRequest(
        String input, String sessionId, String principal, String tenant, String traceId) {

    /** A request carrying just the input; identity/session default on the server. */
    public static A2aRequest of(String input) {
        return new A2aRequest(input, null, null, null, null);
    }
}
