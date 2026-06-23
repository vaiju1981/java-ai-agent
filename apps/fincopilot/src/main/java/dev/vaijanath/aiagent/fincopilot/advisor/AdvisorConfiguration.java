package dev.vaijanath.aiagent.fincopilot.advisor;

import dev.vaijanath.aiagent.fincopilot.FinCopilotProperties;
import dev.vaijanath.aiagent.langchain4j.OllamaModelPorts;
import dev.vaijanath.aiagent.rag.Embedder;
import dev.vaijanath.aiagent.rag.InMemoryVectorStore;
import dev.vaijanath.aiagent.rag.Retriever;
import dev.vaijanath.aiagent.tool.Tool;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the Advisor's grounding: an {@link Embedder}, the curated {@link FinanceKnowledgeBase} loaded
 * into an in-memory vector store, and the {@code guidance_search} {@link Tool} the starter adds to the
 * agent — so the model can ground budgeting/debt/saving advice in cited passages.
 *
 * <p>The embedder prefers a configured Ollama embedding model (semantic retrieval) and falls back to the
 * offline {@link HashingEmbedder} (lexical) if none is set or the model is unreachable.
 */
@Configuration
class AdvisorConfiguration {

    private static final Logger log = LoggerFactory.getLogger(AdvisorConfiguration.class);

    @Bean
    Embedder embedder(FinCopilotProperties properties) {
        String embedModel = properties.embedModel();
        if (embedModel == null || embedModel.isBlank()) {
            return new HashingEmbedder(256);
        }
        try {
            Embedder ollama = OllamaModelPorts.ollamaEmbedder(properties.ollamaBaseUrl(), embedModel);
            ollama.embed("warmup"); // fail fast if the model is not reachable / pulled
            log.info("FinCopilot retrieval uses the Ollama embedding model '{}'", embedModel);
            return ollama;
        } catch (RuntimeException e) {
            log.warn(
                    "Ollama embedding model '{}' unavailable ({}); using the offline hashing embedder",
                    embedModel,
                    e.toString());
            return new HashingEmbedder(256);
        }
    }

    @Bean
    Retriever knowledgeBase(Embedder embedder) {
        InMemoryVectorStore kb = new InMemoryVectorStore(embedder);
        for (FinanceKnowledgeBase.Article article : FinanceKnowledgeBase.articles()) {
            kb.add("kb", article.id(), article.title() + ". " + article.text(), Map.of("title", article.title()));
        }
        return kb;
    }

    @Bean
    Tool guidanceSearchTool(Retriever knowledgeBase) {
        return new KbSearchTool(knowledgeBase, 3);
    }
}
