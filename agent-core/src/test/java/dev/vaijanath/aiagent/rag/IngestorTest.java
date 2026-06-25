package dev.vaijanath.aiagent.rag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class IngestorTest {

    /** Records every (id, text, metadata) written, so we can assert what ingestion produced. */
    private static final class RecordingStore implements ChunkStore {
        final List<String> ids = new ArrayList<>();
        final List<String> texts = new ArrayList<>();
        final List<Map<String, String>> metas = new ArrayList<>();

        @Override
        public void store(String tenant, String id, String text, Map<String, String> metadata) {
            ids.add(id);
            texts.add(text);
            metas.add(metadata);
        }
    }

    /** A trivial letter-frequency embedder so similar text scores high — enough for the integration test. */
    private static float[] freq(String text) {
        float[] v = new float[26];
        for (char c : text.toLowerCase().toCharArray()) {
            if (c >= 'a' && c <= 'z') {
                v[c - 'a']++;
            }
        }
        return v;
    }

    @Test
    void ingestSplitsAndWritesChunksWithDerivedIds() {
        RecordingStore store = new RecordingStore();
        Ingestor ingestor = new Ingestor(DocumentSplitter.ofChars(20, 5));

        int n = ingestor.ingest(
                "acme", new Document("manual", "aaaa bbbb cccc dddd eeee ffff"), store);

        assertEquals(2, n);
        assertEquals(List.of("manual#0", "manual#1"), store.ids);
    }

    @Test
    void documentMetadataRidesOnEveryChunk() {
        RecordingStore store = new RecordingStore();
        Ingestor ingestor = new Ingestor(DocumentSplitter.ofChars(20, 5));
        Map<String, String> meta = Map.of("source", "manual.md");

        ingestor.ingest("acme", new Document("manual", "aaaa bbbb cccc dddd eeee ffff", meta), store);

        assertFalse(store.metas.isEmpty());
        store.metas.forEach(m -> assertEquals("manual.md", m.get("source")));
    }

    @Test
    void ingestAllSumsChunkCounts() {
        RecordingStore store = new RecordingStore();
        Ingestor ingestor = new Ingestor(DocumentSplitter.ofChars(100, 0));

        int total = ingestor.ingestAll(
                "acme", List.of(new Document("a", "short one"), new Document("b", "short two")), store);

        assertEquals(2, total); // each short doc -> a single chunk
        assertEquals(List.of("a#0", "b#0"), store.ids);
    }

    @Test
    void documentNormalizesNullMetadataToEmpty() {
        assertTrue(new Document("id", "text", null).metadata().isEmpty());
    }

    @Test
    void wiresIntoAnInMemoryVectorStoreByMethodReference() {
        InMemoryVectorStore store = new InMemoryVectorStore(IngestorTest::freq);
        Ingestor ingestor = new Ingestor(DocumentSplitter.ofChars(30, 5));

        int n = ingestor.ingest(
                "acme",
                new Document("kb", "alpha bravo charlie. delta echo foxtrot. golf hotel india juliet."),
                store::add);

        assertTrue(n >= 2);
        List<RetrievedChunk> hits = store.retrieve("acme", "charlie", 3);
        assertFalse(hits.isEmpty()); // the ingested chunks are retrievable
        assertTrue(hits.get(0).id().startsWith("kb#"));
    }
}
