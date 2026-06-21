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
}
