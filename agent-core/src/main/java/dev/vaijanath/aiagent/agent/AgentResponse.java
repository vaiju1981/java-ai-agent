package dev.vaijanath.aiagent.agent;

/**
 * The result of an agent turn.
 *
 * @param output     the text to show the user (a real answer, or a guardrail's safe replacement)
 * @param blocked    true if a guardrail stopped the turn
 * @param stopReason why the turn ended ({@code "completed"}, {@code "max_steps"}, or a guardrail reason)
 */
public record AgentResponse(String output, boolean blocked, String stopReason) {

    public static AgentResponse completed(String output) {
        return new AgentResponse(output, false, "completed");
    }

    public static AgentResponse blocked(String replacement, String reason) {
        return new AgentResponse(replacement, true, reason);
    }

    public static AgentResponse stopped(String output, String reason) {
        return new AgentResponse(output, false, reason);
    }

    /** True only for a genuine completion — not blocked, and not a stop (max_steps, model_error, …). */
    public boolean isCompleted() {
        return !blocked && "completed".equals(stopReason);
    }

    /** The stable, machine-readable classification of how this turn ended. */
    public StopReason reason() {
        return StopReason.of(this);
    }

    /** Whether re-issuing the same request is reasonable (the failure is likely transient). */
    public boolean retryable() {
        return reason().retryable();
    }
}
