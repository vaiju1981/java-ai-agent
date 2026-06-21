package dev.vaijanath.aiagent.langchain4j;

import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.vaijanath.aiagent.model.ModelPort;
import java.time.Duration;

/** Convenience factory for a local, Ollama-backed {@link ModelPort}. */
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
}
