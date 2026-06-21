package dev.vaijanath.aiagent.adk;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.adk.events.Event;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Verifies the event-stream → answer reduction with real ADK {@link Event}/{@link Content} objects.
 * (The full {@code run()} path drives a live ADK runner/model, which a unit test can't host.)
 */
class AdkAgentTest {

    private static Event textEvent(String text) {
        return Event.builder().author("agent").content(Content.fromParts(Part.fromText(text))).build();
    }

    @Test
    void reducesEventStreamToTheAnswer() {
        String out = AdkAgent.extractFinalText(List.of(textEvent("thinking..."), textEvent("the answer")));
        assertEquals("the answer", out);
    }

    @Test
    void emptyStreamYieldsEmptyText() {
        assertEquals("", AdkAgent.extractFinalText(List.of()));
    }
}
