package dev.vaijanath.aiagent.fincopilot;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the FinCopilot service. The LLM substrate is Ollama (per the v0.2.0 plan): one
 * model — {@code gemma4:31b-cloud} by default — serves every role; it is a single, swappable value.
 *
 * <p>The agent's own knobs (system prompt, model/tool timeouts, max steps) come from {@code agent.*}
 * (the agent-spring-boot-starter); this holds only what FinCopilot's own beans need.
 */
@ConfigurationProperties("fincopilot")
public record FinCopilotProperties(
        String ollamaBaseUrl,
        String model,
        int historyTurns,
        Duration requestTimeout,
        String auditFile,
        int dailyRequestQuota,
        String embedModel) {

    public FinCopilotProperties {
        ollamaBaseUrl = blankTo(ollamaBaseUrl, "http://localhost:11434");
        model = blankTo(model, "gemma4:31b-cloud");
        historyTurns = historyTurns > 0 ? historyTurns : 20;
        requestTimeout = positiveOr(requestTimeout, Duration.ofSeconds(90));
        auditFile = blankTo(auditFile, "var/audit/fincopilot-events.log");
        dailyRequestQuota = Math.max(0, dailyRequestQuota); // 0 disables the per-user quota
        // Blank -> the offline hashing embedder; otherwise an Ollama embedding model (semantic retrieval).
        embedModel = embedModel == null ? "" : embedModel.strip();
    }

    private static String blankTo(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static Duration positiveOr(Duration value, Duration fallback) {
        return value == null || value.isZero() || value.isNegative() ? fallback : value;
    }
}
