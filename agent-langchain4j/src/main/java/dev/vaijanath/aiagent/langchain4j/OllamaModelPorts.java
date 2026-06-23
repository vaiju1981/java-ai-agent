package dev.vaijanath.aiagent.langchain4j;

import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.ollama.OllamaEmbeddingModel;
import dev.langchain4j.model.ollama.OllamaStreamingChatModel;
import dev.vaijanath.aiagent.model.ModelPort;
import dev.vaijanath.aiagent.model.StreamingModelPort;
import dev.vaijanath.aiagent.rag.Embedder;
import java.time.Duration;

/** Convenience factories for local, Ollama-backed model ports. */
public final class OllamaModelPorts {

    private OllamaModelPorts() {}

    public static ModelPort ollama(String baseUrl, String modelName) {
        OllamaChatModel model = OllamaChatModel.builder()
                .baseUrl(baseUrl)
                .modelName(modelName)
                .timeout(Duration.ofMinutes(2))
                .build();
        return new LangChain4jModelPort(model, "ollama:" + modelName);
    }

    /** A streaming Ollama port (forwards tokens as they arrive). */
    public static StreamingModelPort ollamaStreaming(String baseUrl, String modelName) {
        OllamaStreamingChatModel model = OllamaStreamingChatModel.builder()
                .baseUrl(baseUrl)
                .modelName(modelName)
                .build();
        return new LangChain4jStreamingModelPort(model, "ollama-stream:" + modelName);
    }

    /** Structured output (typed JSON, no parsing) over a local Ollama model. */
    public static dev.vaijanath.aiagent.model.StructuredOutput ollamaStructured(String baseUrl, String modelName) {
        OllamaChatModel model = OllamaChatModel.builder()
                .baseUrl(baseUrl)
                .modelName(modelName)
                .timeout(Duration.ofMinutes(2))
                .build();
        return new LangChain4jStructuredOutput(model);
    }

    /** An {@link Embedder} backed by a local Ollama embedding model (e.g. {@code nomic-embed-text-v2-moe}). */
    public static Embedder ollamaEmbedder(String baseUrl, String modelName) {
        OllamaEmbeddingModel model = OllamaEmbeddingModel.builder()
                .baseUrl(baseUrl)
                .modelName(modelName)
                .timeout(Duration.ofMinutes(2))
                .build();
        return new LangChain4jEmbedder(model);
    }
}
