package dev.vaijanath.aiagent.langchain4j;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.vaijanath.aiagent.memory.Episode;
import dev.vaijanath.aiagent.memory.EpisodicStore;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class LangChain4jEpisodicStoreTest {

    /** Deterministic 3-dim "topic" embeddings, so similarity ordering is predictable. */
    private static EmbeddingModel fakeEmbeddings() {
        return segments -> {
            List<Embedding> out = new ArrayList<>();
            for (TextSegment s : segments) {
                String t = s.text().toLowerCase(Locale.ROOT);
                float capital = (t.contains("capital") || t.contains("australia") || t.contains("canberra")) ? 1f : 0f;
                float weather = (t.contains("weather") || t.contains("rain") || t.contains("forecast")) ? 1f : 0f;
                float bread = (t.contains("bread") || t.contains("bake")) ? 1f : 0f;
                out.add(Embedding.from(new float[] {capital, weather, bread}));
            }
            return Response.from(out);
        };
    }

    @Test
    void recallsSemanticallyClosestEpisode() {
        EpisodicStore store = new LangChain4jEpisodicStore(fakeEmbeddings());
        store.record(new Episode("weather in Paris", "rain", true, "check the forecast"));
        store.record(new Episode("capital of Australia", "Sydney", false, "the capital is Canberra"));

        // Worded differently than the stored task, but semantically about the capital.
        List<Episode> hits = store.recall("which city is Australia's seat of government", 1);

        assertEquals(1, hits.size());
        assertTrue(hits.get(0).lesson().contains("Canberra"));
    }
}
