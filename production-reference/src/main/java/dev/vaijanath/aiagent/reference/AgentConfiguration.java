package dev.vaijanath.aiagent.reference;

import dev.vaijanath.aiagent.agent.Agent;
import dev.vaijanath.aiagent.agent.ProductionAgentRuntime;
import dev.vaijanath.aiagent.audit.AsyncAuditSink;
import dev.vaijanath.aiagent.audit.FileAuditSink;
import dev.vaijanath.aiagent.guardrail.CrisisGuardrail;
import dev.vaijanath.aiagent.guardrail.Guardrail;
import dev.vaijanath.aiagent.guardrail.Guardrails;
import dev.vaijanath.aiagent.guardrail.PiiScrubGuardrail;
import dev.vaijanath.aiagent.langchain4j.OllamaModelPorts;
import dev.vaijanath.aiagent.memory.ConversationStore;
import dev.vaijanath.aiagent.model.ModelPort;
import dev.vaijanath.aiagent.model.ResilientModelPort;
import dev.vaijanath.aiagent.observe.AgentObserver;
import dev.vaijanath.aiagent.store.jdbc.JdbcConversationStore;
import dev.vaijanath.aiagent.tools.jsonschema.JsonSchemaToolValidator;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(AgentProperties.class)
class AgentConfiguration {

    private static final Logger log = LoggerFactory.getLogger(AgentConfiguration.class);

    @Bean
    ModelPort modelPort(AgentProperties properties) {
        return OllamaModelPorts.ollama(properties.ollamaBaseUrl(), properties.model());
    }

    @Bean
    ConversationStore conversationStore(DataSource dataSource, AgentProperties properties) {
        // Flyway owns schema changes. This constructor intentionally performs no DDL.
        return new JdbcConversationStore(dataSource::getConnection, properties.historyTurns());
    }

    @Bean(destroyMethod = "close")
    AsyncAuditSink auditSink(AgentProperties properties) throws IOException {
        Path path = Path.of(properties.auditFile()).toAbsolutePath();
        if (path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }
        return new AsyncAuditSink(new FileAuditSink(path));
    }

    @Bean
    Agent productionAgent(
            ModelPort model,
            ConversationStore conversations,
            AsyncAuditSink audit,
            MeterRegistry meterRegistry,
            AgentProperties properties) {
        return baseBuilder(model, conversations, audit, meterRegistry, properties).build();
    }

    /**
     * Builds a per-request agent that carries an extra {@link AgentObserver} alongside the standard
     * configuration — used by the streaming endpoint to forward turn events to one SSE client without
     * those events leaking across concurrent requests (the observer is bound to that turn's agent).
     */
    @Bean
    Function<AgentObserver, Agent> streamingAgentFactory(
            ModelPort model,
            ConversationStore conversations,
            AsyncAuditSink audit,
            MeterRegistry meterRegistry,
            AgentProperties properties) {
        return observer ->
                baseBuilder(model, conversations, audit, meterRegistry, properties).observer(observer).build();
    }

    /** Runs streaming turns off the request thread so the SSE response can be returned immediately. */
    @Bean(destroyMethod = "close")
    ExecutorService streamExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    private ProductionAgentRuntime.Builder baseBuilder(
            ModelPort model,
            ConversationStore conversations,
            AsyncAuditSink audit,
            MeterRegistry meterRegistry,
            AgentProperties properties) {
        ProductionAgentRuntime.Builder builder = ProductionAgentRuntime.builder()
                .model(model)
                .conversationStore(conversations)
                .auditSink(audit)
                .argumentValidator(new JsonSchemaToolValidator())
                .modelTimeout(properties.modelTimeout())
                .toolTimeout(properties.toolTimeout())
                .observer(new MeterRegistryAgentObserver(meterRegistry))
                .systemPrompt("You are a concise, accurate production assistant. Never invent tool results.");
        contentGuardrails(properties).forEach(builder::guardrail);
        return builder;
    }

    /**
     * The content-safety pipeline. Crisis detection and PII scrubbing need no model and always run; a
     * configured Llama Guard model adds the classifier (the full kidguard pipeline). The guard model
     * is wrapped in {@link ResilientModelPort} so a slow classifier degrades rather than stalls.
     */
    private List<Guardrail> contentGuardrails(AgentProperties properties) {
        if (properties.hasGuardModel()) {
            ModelPort guard = new ResilientModelPort(
                    OllamaModelPorts.ollama(properties.ollamaBaseUrl(), properties.guardModel()),
                    2, properties.modelTimeout(), 200);
            return Guardrails.kidguard(guard);
        }
        List<Guardrail> guardrails = new ArrayList<>();
        guardrails.add(new CrisisGuardrail());
        guardrails.add(new PiiScrubGuardrail());
        return guardrails;
    }

    /** Readiness reflects model reachability (contributes to the {@code readiness} group as "model"). */
    @Bean
    ModelHealthIndicator model(AgentProperties properties) {
        return new ModelHealthIndicator(properties.ollamaBaseUrl());
    }

    @Bean
    FilterRegistrationBean<ApiKeyAuthenticationFilter> apiKeyFilter(AgentProperties properties) {
        FilterRegistrationBean<ApiKeyAuthenticationFilter> registration =
                new FilterRegistrationBean<>(new ApiKeyAuthenticationFilter(properties.apiKeys()));
        registration.addUrlPatterns("/api/*");
        registration.setOrder(10);
        return registration;
    }

    @Bean
    FilterRegistrationBean<RateLimitFilter> rateLimitFilter(AgentProperties properties) {
        FilterRegistrationBean<RateLimitFilter> registration = new FilterRegistrationBean<>(
                new RateLimitFilter(properties.rateLimitPerMinute(), properties.maxRequestBytes()));
        registration.addUrlPatterns("/api/*");
        registration.setOrder(20);
        return registration;
    }

    /** Surfaces insecure-by-default configuration loudly at startup instead of failing silently. */
    @Bean
    ApplicationRunner configurationWarnings(
            AgentProperties properties, @Value("${spring.datasource.password:}") String dbPassword) {
        return args -> {
            if (properties.apiKeys().isEmpty()) {
                log.warn("SECURITY: no agent.api-keys configured — /api is UNAUTHENTICATED. Set "
                        + "agent.api-keys, or terminate auth at a trusted gateway, before exposing this service.");
            }
            if ("agent".equals(dbPassword)) {
                log.warn("SECURITY: datasource is using the insecure default password 'agent'. Set "
                        + "DATABASE_PASSWORD for any non-local deployment.");
            }
            if (!properties.hasGuardModel()) {
                log.warn("SAFETY: no agent.guard-model configured — running with crisis + PII guardrails "
                        + "only. Set agent.guard-model (e.g. llama-guard3:1b) to enable the Llama Guard classifier.");
            }
        };
    }
}
