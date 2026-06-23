package dev.vaijanath.aiagent.fincopilot.advisor;

import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vaijanath.aiagent.langchain4j.OllamaModelPorts;
import dev.vaijanath.aiagent.rag.Embedder;
import dev.vaijanath.aiagent.rag.InMemoryVectorStore;
import dev.vaijanath.aiagent.rag.RetrievedChunk;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * Confirms the Ollama embedder gives genuine semantic retrieval: a query that shares no salient words
 * with the article still surfaces it. Gated on {@code OLLAMA_TOOLCALL_PROBE} (needs the embedding model
 * pulled, e.g. {@code nomic-embed-text-v2-moe}).
 */
class SemanticRetrievalTest {

    @Test
    @EnabledIfEnvironmentVariable(named = "OLLAMA_TOOLCALL_PROBE", matches = ".+")
    void semanticQueryFindsTheRightArticleWithoutSharedWords() {
        String baseUrl = System.getenv().getOrDefault("OLLAMA_BASE_URL", "http://localhost:11434");
        String embedModel = System.getenv().getOrDefault("FINCOPILOT_EMBED_MODEL", "nomic-embed-text-v2-moe");
        Embedder embedder = OllamaModelPorts.ollamaEmbedder(baseUrl, embedModel);

        InMemoryVectorStore kb = new InMemoryVectorStore(embedder);
        for (FinanceKnowledgeBase.Article a : FinanceKnowledgeBase.articles()) {
            kb.add("kb", a.id(), a.title() + ". " + a.text(), Map.of("title", a.title()));
        }

        // "rainy day money" shares no salient words with the emergency-fund text, but is semantically close.
        List<RetrievedChunk> hits = kb.retrieve("kb", "money set aside for a rainy day", 2);

        System.out.println("[semantic] " + hits.stream().map(RetrievedChunk::id).toList());
        assertTrue(
                hits.stream().anyMatch(h -> h.id().equals("emergency-fund")),
                "semantic match should surface emergency-fund among the top hits");
    }
}
