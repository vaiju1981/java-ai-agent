package dev.vaijanath.aiagent.demos.analytics;

import dev.vaijanath.aiagent.langchain4j.OllamaModelPorts;
import dev.vaijanath.aiagent.model.ModelPort;
import java.util.Locale;

/**
 * Config-driven model selection — no default. The provider is chosen by env var so the same app runs
 * against any backend. Only Ollama is wired into this build (the dev/test backend); Anthropic/OpenAI
 * fail with a clear message rather than a silent fake.
 */
final class AnalyticsModels {

    private AnalyticsModels() {}

    static ModelPort fromEnv() {
        String provider = System.getenv("ANALYTICS_MODEL_PROVIDER");
        if (provider == null || provider.isBlank()) {
            throw new IllegalStateException(
                    "set ANALYTICS_MODEL_PROVIDER (ollama) and ANALYTICS_MODEL (e.g. gemma4:31b-cloud)");
        }
        String model = System.getenv().getOrDefault("ANALYTICS_MODEL", "");
        if (model.isBlank()) {
            throw new IllegalStateException("set ANALYTICS_MODEL (e.g. gemma4:31b-cloud)");
        }
        return switch (provider.toLowerCase(Locale.ROOT)) {
            case "ollama" -> OllamaModelPorts.ollama(
                    System.getenv().getOrDefault("OLLAMA_BASE_URL", "http://localhost:11434"), model);
            default -> throw new IllegalStateException("model provider '" + provider
                    + "' is not enabled in this build (only 'ollama' is wired); "
                    + "add agent-anthropic / agent-openai to enable it");
        };
    }
}
