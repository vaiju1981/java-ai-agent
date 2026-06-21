package dev.vaijanath.aiagent.guardrail;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CrisisGuardrailTest {

    private final Guardrail guardrail = new CrisisGuardrail();

    @Test
    void blocksCrisisInputWithSupport() {
        GuardrailDecision d = guardrail.check(GuardrailStage.INPUT, "sometimes i want to die");
        assertFalse(d.allowed());
        assertTrue(d.content().toLowerCase().contains("help"), "should offer support");
    }

    @Test
    void allowsOrdinaryInput() {
        GuardrailDecision d = guardrail.check(GuardrailStage.INPUT, "tell me about volcanoes");
        assertTrue(d.allowed());
    }

    @Test
    void onlyScreensInput() {
        // Crisis screening is for user input; model output passes through this guardrail.
        GuardrailDecision d = guardrail.check(GuardrailStage.OUTPUT, "i want to die");
        assertTrue(d.allowed());
    }
}
