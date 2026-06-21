package dev.vaijanath.aiagent.memory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class InMemoryEpisodicStoreTest {

    @Test
    void recallsTheRelevantEpisode() {
        InMemoryEpisodicStore store = new InMemoryEpisodicStore();
        store.record(new Episode("weather in Paris", "rainy", true, "check the forecast first"));
        store.record(new Episode("capital of Australia", "Sydney", false,
                "the capital of Australia is Canberra"));

        List<Episode> hits = store.recall("what is the capital of Australia", 5);

        assertEquals(1, hits.size());
        assertTrue(hits.get(0).lesson().contains("Canberra"));
    }

    @Test
    void returnsEmptyWhenNothingRelevant() {
        InMemoryEpisodicStore store = new InMemoryEpisodicStore();
        store.record(new Episode("baking bread", "good", true, "let the dough rest"));
        assertTrue(store.recall("quantum chromodynamics lecture notes", 5).isEmpty());
    }

    @Test
    void recallIsScopedToTenant() {
        InMemoryEpisodicStore store = new InMemoryEpisodicStore();
        store.record(new Episode("tenant-a", "deploy the service", "ok", false, "run migrations first"));
        store.record(new Episode("tenant-b", "deploy the service", "ok", false, "tenant-b only lesson"));

        List<Episode> a = store.recall("tenant-a", "deploy the service", 5);

        assertEquals(1, a.size(), "must not see the other tenant's lesson");
        assertEquals("run migrations first", a.get(0).lesson());
    }
}
