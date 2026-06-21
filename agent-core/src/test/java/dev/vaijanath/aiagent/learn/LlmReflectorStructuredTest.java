package dev.vaijanath.aiagent.learn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vaijanath.aiagent.model.ModelRequest;
import dev.vaijanath.aiagent.model.StructuredOutput;
import org.junit.jupiter.api.Test;

class LlmReflectorStructuredTest {

    private static StructuredOutput returning(LlmReflector.Verdict verdict) {
        return new StructuredOutput() {
            @Override
            public <T> T generate(ModelRequest request, Class<T> type) {
                return type.cast(verdict);
            }
        };
    }

    @Test
    void structuredOkVerdict() {
        Reflection r = new LlmReflector(returning(new LlmReflector.Verdict(true, ""))).reflect("t", "a");
        assertTrue(r.satisfactory());
    }

    @Test
    void structuredIssueVerdictCarriesLesson() {
        Reflection r = new LlmReflector(returning(new LlmReflector.Verdict(false, "fix it"))).reflect("t", "a");
        assertFalse(r.satisfactory());
        assertEquals("fix it", r.lesson());
    }
}
