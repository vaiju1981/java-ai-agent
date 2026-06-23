package dev.vaijanath.aiagent.fincopilot.advisor;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vaijanath.aiagent.rag.InMemoryVectorStore;
import dev.vaijanath.aiagent.rag.Retriever;
import dev.vaijanath.aiagent.tool.ToolResult;
import java.util.Map;
import org.junit.jupiter.api.Test;

class KbSearchTest {

    private static Retriever knowledgeBase() {
        InMemoryVectorStore kb = new InMemoryVectorStore(new HashingEmbedder(256));
        for (FinanceKnowledgeBase.Article a : FinanceKnowledgeBase.articles()) {
            kb.add("kb", a.id(), a.title() + ". " + a.text(), Map.of("title", a.title()));
        }
        return kb;
    }

    @Test
    void retrievesRelevantGuidanceWithCitableTitle() {
        ToolResult r = new KbSearchTool(knowledgeBase(), 3).invoke("{\"query\":\"how big should my emergency fund be\"}");
        assertFalse(r.error());
        assertTrue(r.content().contains("[Emergency fund]"), r.content());
    }

    @Test
    void debtQuestionRetrievesDebtGuidance() {
        ToolResult r =
                new KbSearchTool(knowledgeBase(), 2).invoke("{\"query\":\"should I pay the highest interest debt first\"}");
        String lower = r.content().toLowerCase();
        assertTrue(lower.contains("avalanche") || lower.contains("debt"), r.content());
    }

    @Test
    void blankQueryIsAnError() {
        assertTrue(new KbSearchTool(knowledgeBase(), 3).invoke("{}").error());
    }
}
