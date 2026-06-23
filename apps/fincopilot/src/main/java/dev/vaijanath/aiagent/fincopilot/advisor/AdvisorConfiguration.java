package dev.vaijanath.aiagent.fincopilot.advisor;

import dev.vaijanath.aiagent.rag.Embedder;
import dev.vaijanath.aiagent.rag.InMemoryVectorStore;
import dev.vaijanath.aiagent.rag.Retriever;
import dev.vaijanath.aiagent.tool.Tool;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the Advisor's grounding: an {@link Embedder}, the curated {@link FinanceKnowledgeBase} loaded
 * into an in-memory vector store, and the {@code guidance_search} {@link Tool} the starter adds to the
 * agent — so the model can ground budgeting/debt/saving advice in cited passages.
 */
@Configuration
class AdvisorConfiguration {

    @Bean
    Embedder embedder() {
        return new HashingEmbedder(256);
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
