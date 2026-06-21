package dev.vaijanath.aiagent.memory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileEpisodicStoreTest {

    @Test
    void persistsAcrossInstances(@TempDir Path dir) {
        Path file = dir.resolve("episodes.tsv");
        new FileEpisodicStore(file)
                .record(new Episode("capital of Australia", "Sydney", false,
                        "the capital of Australia is Canberra"));

        // A fresh instance simulates a new session/process.
        List<Episode> hits = new FileEpisodicStore(file).recall("what is the capital of Australia", 5);

        assertEquals(1, hits.size());
        assertTrue(hits.get(0).lesson().contains("Canberra"));
    }

    @Test
    void roundTripsTabsAndNewlines(@TempDir Path dir) {
        Path file = dir.resolve("e.tsv");
        Episode tricky = new Episode("multi line task", "out", true, "lesson with\ttab and\nnewline");
        new FileEpisodicStore(file).record(tricky);

        List<Episode> hits = new FileEpisodicStore(file).recall("multi line task lesson", 5);

        assertEquals(1, hits.size());
        assertEquals("lesson with\ttab and\nnewline", hits.get(0).lesson());
    }
}
