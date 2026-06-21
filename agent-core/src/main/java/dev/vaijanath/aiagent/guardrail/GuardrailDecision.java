package dev.vaijanath.aiagent.guardrail;

/**
 * The outcome of a guardrail check.
 *
 * <ul>
 *   <li>{@code allow(content)} — pass through, possibly transformed (e.g. PII-scrubbed).</li>
 *   <li>{@code block(replacement, reason)} — stop and show the safe replacement to the user.</li>
 * </ul>
 */
public record GuardrailDecision(boolean allowed, String content, String reason) {

    public static GuardrailDecision allow(String content) {
        return new GuardrailDecision(true, content, null);
    }

    public static GuardrailDecision block(String replacement, String reason) {
        return new GuardrailDecision(false, replacement, reason);
    }

    public boolean blocked() {
        return !allowed;
    }
}
