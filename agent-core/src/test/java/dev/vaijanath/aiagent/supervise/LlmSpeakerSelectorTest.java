package dev.vaijanath.aiagent.supervise;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import dev.vaijanath.aiagent.model.ModelResponse;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LlmSpeakerSelectorTest {

    private static final Map<String, String> ROSTER = Map.of("alice", "researcher", "bob", "writer");

    @Test
    void picksTheNamedSpeaker() {
        SpeakerSelector selector = new LlmSpeakerSelector(request -> ModelResponse.text("bob"));

        assertEquals("bob", selector.next("task", List.of(), ROSTER));
    }

    @Test
    void endsTheChatOnDone() {
        SpeakerSelector selector = new LlmSpeakerSelector(request -> ModelResponse.text("DONE"));

        assertNull(selector.next("task", List.of(), ROSTER));
    }

    @Test
    void endsTheChatWhenNoNameMatches() {
        SpeakerSelector selector = new LlmSpeakerSelector(request -> ModelResponse.text("nobody here"));

        assertNull(selector.next("task", List.of(), ROSTER));
    }
}
