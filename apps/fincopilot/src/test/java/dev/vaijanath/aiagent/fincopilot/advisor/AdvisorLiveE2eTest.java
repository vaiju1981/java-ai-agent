package dev.vaijanath.aiagent.fincopilot.advisor;

import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vaijanath.aiagent.agent.Agent;
import dev.vaijanath.aiagent.agent.AgentRequest;
import dev.vaijanath.aiagent.agent.AgentResponse;
import dev.vaijanath.aiagent.agent.ProductionAgentRuntime;
import dev.vaijanath.aiagent.audit.InMemoryAuditSink;
import dev.vaijanath.aiagent.langchain4j.OllamaModelPorts;
import dev.vaijanath.aiagent.memory.InMemoryConversationStore;
import dev.vaijanath.aiagent.model.ModelPort;
import dev.vaijanath.aiagent.rag.InMemoryVectorStore;
import dev.vaijanath.aiagent.tools.jsonschema.JsonSchemaToolValidator;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * The Advisor end-to-end: gemma4:31b-cloud calls {@code guidance_search} and grounds its answer in the
 * curated knowledge base. Gated on {@code OLLAMA_TOOLCALL_PROBE}; needs no database (in-memory KB).
 */
class AdvisorLiveE2eTest {

    @Test
    @EnabledIfEnvironmentVariable(named = "OLLAMA_TOOLCALL_PROBE", matches = ".+")
    void advisorGroundsAdviceInTheKnowledgeBase() {
        String baseUrl = System.getenv().getOrDefault("OLLAMA_BASE_URL", "http://localhost:11434");
        String modelName = System.getenv().getOrDefault("OLLAMA_MODEL", "gemma4:31b-cloud");
        ModelPort model = OllamaModelPorts.ollama(baseUrl, modelName);

        InMemoryVectorStore kb = new InMemoryVectorStore(new HashingEmbedder(256));
        for (FinanceKnowledgeBase.Article a : FinanceKnowledgeBase.articles()) {
            kb.add("kb", a.id(), a.title() + ". " + a.text(), Map.of("title", a.title()));
        }

        Agent agent = ProductionAgentRuntime.builder()
                .model(model)
                .conversationStore(new InMemoryConversationStore())
                .auditSink(new InMemoryAuditSink())
                .argumentValidator(new JsonSchemaToolValidator())
                .tool(new KbSearchTool(kb, 3))
                .modelTimeout(Duration.ofSeconds(120))
                .toolTimeout(Duration.ofSeconds(20))
                .maxSteps(6)
                .systemPrompt("You are a finance assistant. For guidance questions, call guidance_search and "
                        + "ground your answer in the returned passages, citing source titles in [brackets]. "
                        + "Add a brief 'general information, not personalized advice' note.")
                .build();

        AgentResponse response = agent.run(new AgentRequest("How should I think about building an emergency fund?"));

        System.out.println("[advisor] " + response.output());
        assertTrue(
                response.output().toLowerCase().contains("emergency"),
                "answer should be grounded in the emergency-fund guidance: " + response.output());
    }
}
