package dev.vaijanath.aiagent.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class MessageTest {

    @Test
    void plainFactoriesCarryNoMedia() {
        assertFalse(Message.system("s").hasMedia());
        assertFalse(Message.user("u").hasMedia());
        assertFalse(Message.assistant("a").hasMedia());
        assertFalse(Message.toolResult("id", "tool", "r").hasMedia());
        assertTrue(Message.user("u").media().isEmpty());
    }

    @Test
    void userWithMediaCarriesTheParts() {
        Media img = Media.imageUrl("https://example.com/cat.png");
        Message m = Message.user("describe", List.of(img));

        assertEquals(Role.USER, m.role());
        assertEquals("describe", m.content());
        assertTrue(m.hasMedia());
        assertEquals(List.of(img), m.media());
    }

    @Test
    void mediaIsDefensivelyCopiedAndImmutable() {
        List<Media> source = new ArrayList<>();
        source.add(Media.imageUrl("https://example.com/a.png"));
        Message m = Message.user("x", source);

        source.add(Media.imageUrl("https://example.com/b.png")); // mutating the source must not leak in
        assertEquals(1, m.media().size());
        assertThrows(UnsupportedOperationException.class,
                () -> m.media().add(Media.imageUrl("https://example.com/c.png")));
    }

    @Test
    void nullMediaBecomesEmpty() {
        Message m = new Message(Role.USER, "x", null, null, null, null);
        assertTrue(m.media().isEmpty());
        assertFalse(m.hasMedia());
    }

    @Test
    void toolCallsStillWorkAlongsideMedia() {
        ToolCall call = new ToolCall("c1", "search", "{\"q\":\"x\"}");
        Message m = Message.assistant("", List.of(call));

        assertTrue(m.hasToolCalls());
        assertFalse(m.hasMedia());
    }
}
