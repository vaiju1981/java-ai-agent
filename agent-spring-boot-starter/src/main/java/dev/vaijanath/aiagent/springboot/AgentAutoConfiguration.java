package dev.vaijanath.aiagent.springboot;

import dev.vaijanath.aiagent.agent.Agent;
import dev.vaijanath.aiagent.agent.ProductionAgentRuntime;
import dev.vaijanath.aiagent.audit.AuditSink;
import dev.vaijanath.aiagent.audit.InMemoryAuditSink;
import dev.vaijanath.aiagent.guardrail.Guardrail;
import dev.vaijanath.aiagent.memory.ConversationStore;
import dev.vaijanath.aiagent.memory.InMemoryConversationStore;
import dev.vaijanath.aiagent.model.ModelPort;
import dev.vaijanath.aiagent.observe.AgentObserver;
import dev.vaijanath.aiagent.tool.Tool;
import dev.vaijanath.aiagent.tool.ToolArgumentValidator;
import dev.vaijanath.aiagent.tools.jsonschema.JsonSchemaToolValidator;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Autoconfigures a governed {@link Agent} once the application provides a {@link ModelPort} bean (from
 * any substrate adapter). Sensible defaults — an in-memory conversation store, an in-memory audit
 * sink, and JSON-schema argument validation — are supplied via {@code @ConditionalOnMissingBean}, so
 * each is overridden simply by declaring your own bean. Any {@link Tool}, {@link Guardrail}, or
 * {@link AgentObserver} beans in the context are wired in automatically.
 *
 * <p>The agent is assembled with {@link ProductionAgentRuntime}: durable-store + audit + argument
 * validation, model/tool timeouts, deny-effectful tool authorization, and a hard per-turn deadline.
 * For production, override the in-memory store and audit sink with durable beans.
 */
@AutoConfiguration
@EnableConfigurationProperties(AgentProperties.class)
public class AgentAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public ConversationStore agentConversationStore() {
        return new InMemoryConversationStore();
    }

    @Bean
    @ConditionalOnMissingBean
    public AuditSink agentAuditSink() {
        return new InMemoryAuditSink();
    }

    @Bean
    @ConditionalOnMissingBean
    public ToolArgumentValidator agentToolArgumentValidator() {
        return new JsonSchemaToolValidator();
    }

    @Bean
    @ConditionalOnBean(ModelPort.class)
    @ConditionalOnMissingBean(Agent.class)
    public Agent agent(
            ModelPort model,
            ConversationStore conversationStore,
            AuditSink auditSink,
            ToolArgumentValidator argumentValidator,
            AgentProperties properties,
            ObjectProvider<Tool> tools,
            ObjectProvider<Guardrail> guardrails,
            ObjectProvider<AgentObserver> observers) {
        ProductionAgentRuntime.Builder builder = ProductionAgentRuntime.builder()
                .model(model)
                .conversationStore(conversationStore)
                .auditSink(auditSink)
                .argumentValidator(argumentValidator)
                .modelTimeout(properties.getModelTimeout())
                .toolTimeout(properties.getToolTimeout())
                .maxSteps(properties.getMaxSteps());
        if (properties.getSystemPrompt() != null && !properties.getSystemPrompt().isBlank()) {
            builder.systemPrompt(properties.getSystemPrompt());
        }
        tools.orderedStream().forEach(builder::tool);
        guardrails.orderedStream().forEach(builder::guardrail);
        observers.orderedStream().forEach(builder::observer);
        return builder.build();
    }
}
