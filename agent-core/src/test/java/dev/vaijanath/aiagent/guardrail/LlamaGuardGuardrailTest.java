package dev.vaijanath.aiagent.guardrail;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vaijanath.aiagent.model.ModelPort;
import dev.vaijanath.aiagent.model.ModelResponse;
import org.junit.jupiter.api.Test;

/** Verifies verdict parsing and fail-closed/open behavior with a fake classifier (no live model). */
class LlamaGuardGuardrailTest {

    @Test
    void allowsSafeVerdict() {
        ModelPort classifier = request -> ModelResponse.text("safe");
        GuardrailDecision d = new LlamaGuardGuardrail(classifier).check(GuardrailStage.INPUT, "hi");
        assertTrue(d.allowed());
    }

    @Test
    void blocksUnsafeVerdictWithCategory() {
        ModelPort classifier = request -> ModelResponse.text("unsafe\nS1");
        GuardrailDecision d = new LlamaGuardGuardrail(classifier).check(GuardrailStage.OUTPUT, "x");
        assertFalse(d.allowed());
        assertTrue(d.reason().contains("S1"), "reason: " + d.reason());
    }

    @Test
    void inconclusiveVerdictFailsClosed() {
        ModelPort classifier = request -> ModelResponse.text("I'm not sure about that.");
        GuardrailDecision d = new LlamaGuardGuardrail(classifier).check(GuardrailStage.INPUT, "x");
        assertFalse(d.allowed(), "a non-safe/non-unsafe verdict should fail closed");
    }

    @Test
    void failsClosedWhenClassifierThrows() {
        ModelPort boom = request -> {
            throw new RuntimeException("classifier down");
        };
        GuardrailDecision d = new LlamaGuardGuardrail(boom).check(GuardrailStage.INPUT, "x");
        assertFalse(d.allowed(), "should block when the safety check is unavailable");
    }

    @Test
    void failsOpenWhenConfigured() {
        ModelPort boom = request -> {
            throw new RuntimeException("classifier down");
        };
        GuardrailDecision d =
                new LlamaGuardGuardrail(boom, "blocked", true).check(GuardrailStage.INPUT, "x");
        assertTrue(d.allowed(), "failOpen should allow when the classifier is unavailable");
    }
}
