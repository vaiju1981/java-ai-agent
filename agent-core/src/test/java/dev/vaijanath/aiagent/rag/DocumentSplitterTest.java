package dev.vaijanath.aiagent.rag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class DocumentSplitterTest {

    @Test
    void blankOrNullYieldsEmpty() {
        DocumentSplitter splitter = DocumentSplitter.ofChars(100, 10);
        assertTrue(splitter.split(null).isEmpty());
        assertTrue(splitter.split("   ").isEmpty());
    }

    @Test
    void shortTextIsASingleTrimmedChunk() {
        List<String> chunks = DocumentSplitter.ofChars(100, 10).split("  a short doc  ");
        assertEquals(List.of("a short doc"), chunks);
    }

    @Test
    void breaksAtWordBoundariesWithOverlap() {
        // maxChars 20, overlap 5: a deterministic split where the boundary is the latest space.
        List<String> chunks =
                DocumentSplitter.ofChars(20, 5).split("aaaa bbbb cccc dddd eeee ffff");

        assertEquals(2, chunks.size());
        assertEquals("aaaa bbbb cccc dddd", chunks.get(0));
        assertEquals("dddd eeee ffff", chunks.get(1)); // "dddd" overlaps from the previous chunk
        chunks.forEach(c -> assertTrue(c.length() <= 20, c));
    }

    @Test
    void prefersAParagraphBoundary() {
        List<String> chunks =
                DocumentSplitter.ofChars(30, 0).split("first para here.\n\nsecond paragraph keeps going");

        assertEquals("first para here.", chunks.get(0)); // broke at the blank line, not mid-sentence
    }

    @Test
    void hardSplitsWhenNoSeparatorFits() {
        String text = "x".repeat(50); // one long token, no separators
        List<String> chunks = DocumentSplitter.ofChars(20, 0).split(text);

        assertEquals(3, chunks.size()); // 20 + 20 + 10
        chunks.forEach(c -> assertTrue(c.length() <= 20));
        assertEquals(50, String.join("", chunks).length()); // overlap 0 -> no content lost or duplicated
    }

    @Test
    void rejectsInvalidConfiguration() {
        assertThrows(IllegalArgumentException.class, () -> DocumentSplitter.ofChars(0, 0));
        assertThrows(IllegalArgumentException.class, () -> DocumentSplitter.ofChars(10, 10));
        assertThrows(IllegalArgumentException.class, () -> DocumentSplitter.ofChars(10, -1));
    }
}
