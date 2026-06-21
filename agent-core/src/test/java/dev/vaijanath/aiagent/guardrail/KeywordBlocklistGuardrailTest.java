package dev.vaijanath.aiagent.guardrail;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class KeywordBlocklistGuardrailTest {

    private final Guardrail guardrail =
            new KeywordBlocklistGuardrail(List.of("badword"), "[blocked]");

    @Test
    void allowsCleanContent() {
        GuardrailDecision d = guardrail.check(GuardrailStage.INPUT, "a clean sentence");
        assertTrue(d.allowed());
    }

    @Test
    void blocksDirtyContentCaseInsensitive() {
        GuardrailDecision d = guardrail.check(GuardrailStage.OUTPUT, "contains BADWORD here");
        assertFalse(d.allowed());
    }
}
