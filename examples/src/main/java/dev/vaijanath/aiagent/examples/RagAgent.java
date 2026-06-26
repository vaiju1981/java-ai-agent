package dev.vaijanath.aiagent.examples;

import dev.vaijanath.aiagent.agent.Agent;
import dev.vaijanath.aiagent.agent.AgentRequest;
import dev.vaijanath.aiagent.agent.DefaultAgent;
import dev.vaijanath.aiagent.langchain4j.OllamaModelPorts;
import dev.vaijanath.aiagent.model.ModelPort;
import dev.vaijanath.aiagent.rag.Embedder;
import dev.vaijanath.aiagent.rag.InMemoryVectorStore;
import dev.vaijanath.aiagent.rag.RetrievalAugmentedAgent;
import dev.vaijanath.aiagent.rag.RetrievedChunk;
import java.util.Locale;

/**
 * Retrieval-augmented answers: a small policy corpus is embedded into an {@link InMemoryVectorStore};
 * {@link RetrievalAugmentedAgent} retrieves the top matches and weaves them into the prompt, so the model
 * answers <b>from the corpus, not its memory</b>.
 *
 * <p>Set {@code AGENT_MODEL} (chat) and {@code AGENT_EMBEDDING_MODEL} (e.g. {@code nomic-embed-text}) for a
 * real, semantic run; with neither it falls back to a deterministic keyword-hashing embedder so the wiring
 * still runs offline. For durable/large corpora use {@code JdbcVectorStore} / {@code PgVectorRetriever}.
 */
public final class RagAgent {

    private RagAgent() {}

    public static void main(String[] args) {
        ModelPort model = Examples.modelFromEnv();
        InMemoryVectorStore kb = new InMemoryVectorStore(embedder(model));
        kb.add("default", "refund", "The refund window is 30 days from delivery, with the receipt.");
        kb.add("default", "shipping", "Standard shipping takes 5-7 business days; express shipping is free over $50.");
        kb.add("default", "warranty", "Electronics include a 1-year limited warranty against manufacturing defects.");

        String question = "What is the refund window?";
        System.out.println("Retrieved for: \"" + question + "\"");
        for (RetrievedChunk hit : kb.retrieve("default", question, 2)) {
            System.out.printf("  [%.3f] %s%n", hit.score(), hit.text());
        }

        Agent base = DefaultAgent.builder()
                .model(model)
                .systemPrompt("Answer only from the provided context; if it isn't there, say so.")
                .build();
        Agent grounded = new RetrievalAugmentedAgent(base, kb);
        System.out.println("\nAnswer: " + grounded.run(new AgentRequest(question)).output());

        if (Examples.isStub(model)) {
            System.out.println("\n(stub model — set AGENT_MODEL + AGENT_EMBEDDING_MODEL for a real semantic run)");
        }
    }

    private static Embedder embedder(ModelPort model) {
        String embeddingModel = System.getenv("AGENT_EMBEDDING_MODEL");
        if (!Examples.isStub(model) && embeddingModel != null && !embeddingModel.isBlank()) {
            String baseUrl = System.getenv().getOrDefault("OLLAMA_BASE_URL", "http://localhost:11434");
            return OllamaModelPorts.ollamaEmbedder(baseUrl, embeddingModel);
        }
        return RagAgent::keywordHash; // deterministic offline fallback (keyword overlap, not semantics)
    }

    private static float[] keywordHash(String text) {
        float[] v = new float[64];
        for (String token : text.toLowerCase(Locale.ROOT).split("\\W+")) {
            if (token.length() >= 3) {
                v[Math.floorMod(token.hashCode(), v.length)] += 1f;
            }
        }
        return v;
    }
}
