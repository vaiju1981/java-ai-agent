package dev.vaijanath.aiagent.supervise;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vaijanath.aiagent.model.ModelPort;
import dev.vaijanath.aiagent.model.ModelRequest;
import dev.vaijanath.aiagent.model.ModelResponse;
import dev.vaijanath.aiagent.model.StructuredOutput;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LlmManagerTest {

    private static final Map<String, String> ROSTER = Map.of("researcher", "facts", "writer", "prose");

    @Test
    void structuredDelegateMapsToADelegateDecision() {
        Manager m = new LlmManager(fixedMove(new LlmManager.Move(false, "researcher", "look it up", null)));

        Manager.Decision d = m.decide("task", List.of(), ROSTER);

        assertFalse(d.done());
        assertEquals("researcher", d.specialist());
        assertEquals("look it up", d.instruction());
    }

    @Test
    void structuredDoneMapsToAFinishDecision() {
        Manager m = new LlmManager(fixedMove(new LlmManager.Move(true, null, null, "final answer")));

        Manager.Decision d = m.decide("task", List.of(), ROSTER);

        assertTrue(d.done());
        assertEquals("final answer", d.answer());
    }

    @Test
    void structuredWithBlankSpecialistIsTreatedAsFinish() {
        Manager m = new LlmManager(fixedMove(new LlmManager.Move(false, "  ", "x", "fallback answer")));

        Manager.Decision d = m.decide("task", List.of(), ROSTER);

        assertTrue(d.done());
        assertEquals("fallback answer", d.answer());
    }

    @Test
    void structuredNullMoveStopsRatherThanLooping() {
        Manager m = new LlmManager(fixedMove(null));

        Manager.Decision d = m.decide("task", List.of(), ROSTER);

        assertTrue(d.done());
        assertEquals("", d.answer());
    }

    @Test
    void freeTextDelegateIsParsed() {
        Manager m = new LlmManager(reply("DELEGATE writer\nwrite the intro"));

        Manager.Decision d = m.decide("task", List.of(), ROSTER);

        assertFalse(d.done());
        assertEquals("writer", d.specialist());
        assertEquals("write the intro", d.instruction());
    }

    @Test
    void freeTextFinishIsParsed() {
        Manager m = new LlmManager(reply("FINISH\nall done here"));

        Manager.Decision d = m.decide("task", List.of(), ROSTER);

        assertTrue(d.done());
        assertEquals("all done here", d.answer());
    }

    @Test
    void freeTextUnrecognizedReplyBecomesTheFinalAnswer() {
        Manager m = new LlmManager(reply("here is just an answer with no verb"));

        Manager.Decision d = m.decide("task", List.of(), ROSTER);

        assertTrue(d.done());
        assertEquals("here is just an answer with no verb", d.answer());
    }

    @Test
    void freeTextBlankReplyStopsRatherThanLooping() {
        Manager m = new LlmManager(reply("   "));

        Manager.Decision d = m.decide("task", List.of(), ROSTER);

        assertTrue(d.done());
        assertEquals("", d.answer());
    }

    /** A {@link StructuredOutput} that always binds to the same {@link LlmManager.Move}. */
    private static StructuredOutput fixedMove(LlmManager.Move move) {
        return new StructuredOutput() {
            @Override
            public <T> T generate(ModelRequest request, Class<T> type) {
                return type.cast(move);
            }
        };
    }

    /** A {@link ModelPort} that always returns the given text. */
    private static ModelPort reply(String text) {
        return request -> ModelResponse.text(text);
    }
}
