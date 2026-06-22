package dev.vaijanath.aiagent.supervise;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.vaijanath.aiagent.model.ModelPort;
import dev.vaijanath.aiagent.model.ModelResponse;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LlmRouterTest {

    private static final Map<String, String> ROSTER = Map.of("weather", "forecasts", "billing", "payments");

    @Test
    void matchesAnExactNameReply() {
        ModelPort model = request -> ModelResponse.text("billing");
        assertEquals("billing", new LlmRouter(model).route("my invoice", ROSTER));
    }

    @Test
    void matchesANameContainedInTheReply() {
        ModelPort model = request -> ModelResponse.text("The billing specialist is best here.");
        assertEquals("billing", new LlmRouter(model).route("my invoice", ROSTER));
    }

    @Test
    void returnsTheRawAnswerWhenNothingMatches() {
        ModelPort model = request -> ModelResponse.text("unsure");
        assertEquals("unsure", new LlmRouter(model).route("hmm", ROSTER));
    }
}
