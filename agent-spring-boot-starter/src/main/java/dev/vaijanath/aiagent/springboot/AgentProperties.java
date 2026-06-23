package dev.vaijanath.aiagent.springboot;

import java.time.Duration;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the autoconfigured agent, bound from {@code agent.*} properties.
 *
 * <p>Validated at startup ({@link #afterPropertiesSet()}): an invalid value (a non-positive timeout or
 * step budget) fails the context fast with a clear message, rather than surfacing as a confusing
 * runtime error on the first turn.
 */
@ConfigurationProperties(prefix = "agent")
public class AgentProperties implements InitializingBean {

    /** Optional system prompt applied to every turn. */
    private String systemPrompt;

    /** Per-model-call timeout. */
    private Duration modelTimeout = Duration.ofSeconds(60);

    /** Per-tool-call timeout. */
    private Duration toolTimeout = Duration.ofSeconds(30);

    /** Maximum tool-calling iterations per turn. */
    private int maxSteps = 8;

    public String getSystemPrompt() {
        return systemPrompt;
    }

    public void setSystemPrompt(String systemPrompt) {
        this.systemPrompt = systemPrompt;
    }

    public Duration getModelTimeout() {
        return modelTimeout;
    }

    public void setModelTimeout(Duration modelTimeout) {
        this.modelTimeout = modelTimeout;
    }

    public Duration getToolTimeout() {
        return toolTimeout;
    }

    public void setToolTimeout(Duration toolTimeout) {
        this.toolTimeout = toolTimeout;
    }

    public int getMaxSteps() {
        return maxSteps;
    }

    public void setMaxSteps(int maxSteps) {
        this.maxSteps = maxSteps;
    }

    /** Fail fast on misconfiguration so a bad value is caught at startup, not on the first turn. */
    @Override
    public void afterPropertiesSet() {
        requirePositive("agent.model-timeout", modelTimeout);
        requirePositive("agent.tool-timeout", toolTimeout);
        if (maxSteps < 1) {
            throw new IllegalStateException("agent.max-steps must be at least 1 (was " + maxSteps + ")");
        }
    }

    private static void requirePositive(String name, Duration value) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalStateException(name + " must be a positive duration (was " + value + ")");
        }
    }
}
