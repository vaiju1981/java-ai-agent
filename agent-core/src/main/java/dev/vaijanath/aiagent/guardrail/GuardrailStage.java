package dev.vaijanath.aiagent.guardrail;

/** When a guardrail runs: on the user's input (before the model) or the model's output. */
public enum GuardrailStage {
    INPUT,
    OUTPUT
}
