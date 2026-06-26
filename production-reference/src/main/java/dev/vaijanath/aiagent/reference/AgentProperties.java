package dev.vaijanath.aiagent.reference;

import java.time.Duration;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("agent")
public record AgentProperties(
        String ollamaBaseUrl,
        String model,
        int historyTurns,
        Duration requestTimeout,
        Duration modelTimeout,
        Duration toolTimeout,
        String auditFile,
        // Content safety: a Llama Guard model enables the full crisis -> PII -> Llama Guard pipeline;
        // blank keeps the model-free crisis + PII scrub guardrails only.
        String guardModel,
        // Edge protection: each API key maps to the tenant it authenticates (so the tenant is bound to
        // the credential, not asserted by a client header). Empty = unauthenticated (with a warning).
        Map<String, String> apiKeys,
        int rateLimitPerMinute,
        long maxRequestBytes,
        // Self-learning: when true, the unary agent is wrapped in a ReflectiveAgent that recalls lessons
        // from past episodes (durable + semantic via JdbcEpisodicStore) and self-corrects on a poor
        // answer. Requires an embedding model; defaults off.
        boolean selfLearning,
        String embeddingModel) {

    public AgentProperties {
        ollamaBaseUrl = defaultIfBlank(ollamaBaseUrl, "http://localhost:11434");
        model = defaultIfBlank(model, "llama3.2");
        historyTurns = historyTurns > 0 ? historyTurns : 20;
        requestTimeout = positiveOr(requestTimeout, Duration.ofSeconds(90));
        modelTimeout = positiveOr(modelTimeout, Duration.ofSeconds(60));
        toolTimeout = positiveOr(toolTimeout, Duration.ofSeconds(15));
        auditFile = defaultIfBlank(auditFile, "var/audit/agent-events.log");
        guardModel = guardModel == null ? "" : guardModel.strip();
        apiKeys = apiKeys == null
                ? Map.of()
                : apiKeys.entrySet().stream()
                        .filter(e -> e.getKey() != null && !e.getKey().isBlank())
                        .collect(Collectors.toUnmodifiableMap(
                                Map.Entry::getKey, e -> defaultIfBlank(e.getValue(), "default")));
        rateLimitPerMinute = Math.max(0, rateLimitPerMinute); // 0 = disabled
        maxRequestBytes = maxRequestBytes > 0 ? maxRequestBytes : 64 * 1024;
        embeddingModel = embeddingModel == null ? "" : embeddingModel.strip();
    }

    /** True when a Llama Guard model is configured, enabling the full safety pipeline. */
    public boolean hasGuardModel() {
        return !guardModel.isBlank();
    }

    /** True when self-learning is enabled and an embedding model is configured to back episodic recall. */
    public boolean hasSelfLearning() {
        return selfLearning && !embeddingModel.isBlank();
    }

    private static String defaultIfBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static Duration positiveOr(Duration value, Duration fallback) {
        return value == null || value.isZero() || value.isNegative() ? fallback : value;
    }
}
