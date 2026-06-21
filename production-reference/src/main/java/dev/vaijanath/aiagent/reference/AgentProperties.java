package dev.vaijanath.aiagent.reference;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("agent")
public record AgentProperties(
        String ollamaBaseUrl,
        String model,
        int historyTurns,
        Duration requestTimeout,
        Duration modelTimeout,
        Duration toolTimeout,
        String auditFile) {

    public AgentProperties {
        ollamaBaseUrl = defaultIfBlank(ollamaBaseUrl, "http://localhost:11434");
        model = defaultIfBlank(model, "llama3.2");
        historyTurns = historyTurns > 0 ? historyTurns : 20;
        requestTimeout = positiveOr(requestTimeout, Duration.ofSeconds(90));
        modelTimeout = positiveOr(modelTimeout, Duration.ofSeconds(60));
        toolTimeout = positiveOr(toolTimeout, Duration.ofSeconds(15));
        auditFile = defaultIfBlank(auditFile, "var/audit/agent-events.log");
    }

    private static String defaultIfBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static Duration positiveOr(Duration value, Duration fallback) {
        return value == null || value.isZero() || value.isNegative() ? fallback : value;
    }
}
