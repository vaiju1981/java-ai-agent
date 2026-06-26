package dev.vaijanath.aiagent.examples;

import dev.vaijanath.aiagent.langchain4j.OllamaModelPorts;
import dev.vaijanath.aiagent.model.ModelPort;
import dev.vaijanath.aiagent.model.StubModelPort;

/** Shared helpers for the examples. */
final class Examples {

    private Examples() {}

    /**
     * A real local model if {@code AGENT_MODEL} (a pulled Ollama model name) is set, else an honest
     * {@link StubModelPort}. {@code OLLAMA_BASE_URL} overrides the default endpoint.
     */
    static ModelPort modelFromEnv() {
        String modelName = System.getenv("AGENT_MODEL");
        if (modelName == null || modelName.isBlank()) {
            return new StubModelPort();
        }
        String baseUrl = System.getenv().getOrDefault("OLLAMA_BASE_URL", "http://localhost:11434");
        return OllamaModelPorts.ollama(baseUrl, modelName);
    }

    /** True when no real model is configured (the {@link StubModelPort} fallback is in use). */
    static boolean isStub(ModelPort port) {
        return port instanceof StubModelPort;
    }
}
