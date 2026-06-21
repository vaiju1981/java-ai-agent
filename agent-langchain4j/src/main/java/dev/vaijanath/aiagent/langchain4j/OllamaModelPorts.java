package dev.vaijanath.aiagent.langchain4j;

import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.ollama.OllamaStreamingChatModel;
import dev.vaijanath.aiagent.model.ModelPort;
import dev.vaijanath.aiagent.model.StreamingModelPort;
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
}
