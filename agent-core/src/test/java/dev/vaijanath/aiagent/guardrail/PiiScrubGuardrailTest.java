package dev.vaijanath.aiagent.guardrail;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PiiScrubGuardrailTest {

    private final Guardrail guardrail = new PiiScrubGuardrail();

    @Test
    void redactsEmailPhoneAndSsn() {
        GuardrailDecision d = guardrail.check(
                GuardrailStage.INPUT,
                "mail me at jane@example.com, call 415-555-1234, ssn 123-45-6789");

        assertTrue(d.allowed());
        assertFalse(d.content().contains("jane@example.com"));
        assertFalse(d.content().contains("415-555-1234"));
        assertFalse(d.content().contains("123-45-6789"));
        assertTrue(d.content().contains("[email]"));
        assertTrue(d.content().contains("[phone]"));
        assertTrue(d.content().contains("[ssn]"));
    }

    @Test
    void leavesOrdinaryTextUnchanged() {
        GuardrailDecision d = guardrail.check(GuardrailStage.INPUT, "What is 23 plus 19?");
        assertTrue(d.allowed());
        assertEquals("What is 23 plus 19?", d.content());
    }

    @Test
    void scrubsLongAdversarialInputInLinearTime() {
        // The previous greedy "[\\w.+-]+@..." pattern took ~1.8s on this 32k input (quadratic
        // backtracking) — a ReDoS. The possessive pattern scans it in single-digit milliseconds.
        String adversarial = "x@" + "a".repeat(32_000);
        long start = System.nanoTime();
        GuardrailDecision d = guardrail.check(GuardrailStage.INPUT, adversarial);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        assertTrue(d.allowed());
        assertTrue(elapsedMs < 500, "PII scrub must be linear; took " + elapsedMs + "ms");
    }
}
