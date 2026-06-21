package dev.vaijanath.aiagent.guardrail;

/**
 * The L3 trust seam. Runs on both input (before the model) and output (before the user) and may
 * allow, transform, or block content.
 *
 * <p>The reference {@code kidguard} safety pipeline (crisis detection, PII scrubbing, blocklist,
 * Llama Guard) will implement this interface so every agent can be made trustworthy by default.
 */
public interface Guardrail {

    GuardrailDecision check(GuardrailStage stage, String content);

    default String name() {
        return getClass().getSimpleName();
    }
}
