package dev.vaijanath.aiagent.springboot;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Configuration for the autoconfigured agent, bound from {@code agent.*} properties. */
@ConfigurationProperties(prefix = "agent")
public class AgentProperties {

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
}
