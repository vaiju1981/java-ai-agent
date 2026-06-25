package dev.vaijanath.aiagent.supervise;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.vaijanath.aiagent.model.ModelResponse;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LlmHandoffTest {

    private static final Map<String, String> ROSTER = Map.of("triage", "routes", "billing", "money");

    @Test
    void transfersWhenTheModelNamesAPeer() {
        Handoff handoff = new LlmHandoff(request -> ModelResponse.text("billing"));

        assertEquals("billing", handoff.next("task", "triage", "answer", ROSTER));
    }

    @Test
    void staysWhenTheModelSaysStay() {
        Handoff handoff = new LlmHandoff(request -> ModelResponse.text("STAY"));

        assertEquals("triage", handoff.next("task", "triage", "answer", ROSTER));
    }

    @Test
    void staysWhenThereAreNoPeers() {
        Handoff handoff = new LlmHandoff(request -> ModelResponse.text("anything"));

        assertEquals("solo", handoff.next("task", "solo", "answer", Map.of("solo", "the only agent")));
    }

    @Test
    void neverTransfersBackToTheCurrentAgent() {
        Handoff handoff = new LlmHandoff(request -> ModelResponse.text("triage"));

        // the model named the current agent; that is not a transfer, so control stays
        assertEquals("triage", handoff.next("task", "triage", "answer", ROSTER));
    }
}
