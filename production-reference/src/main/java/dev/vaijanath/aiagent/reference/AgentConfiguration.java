package dev.vaijanath.aiagent.reference;

import dev.vaijanath.aiagent.agent.Agent;
import dev.vaijanath.aiagent.agent.IdempotencyStore;
import dev.vaijanath.aiagent.agent.IdempotentAgent;
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
import dev.vaijanath.aiagent.springboot.health.ModelEndpointHealthIndicator;
import dev.vaijanath.aiagent.springboot.metrics.MicrometerAgentObserver;
import dev.vaijanath.aiagent.store.jdbc.JdbcConversationStore;
import dev.vaijanath.aiagent.store.jdbc.JdbcIdempotencyStore;
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
import org.springframework.core.env.Environment;

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

    @Bean
    IdempotencyStore idempotencyStore(DataSource dataSource) {
        // Flyway owns schema changes (V2__agent_idempotency.sql). This constructor performs no DDL.
        return new JdbcIdempotencyStore(dataSource::getConnection);
    }

    @Bean(destroyMethod = "close")
    AsyncAuditSink auditSink(AgentProperties properties) throws IOException {
        Path path = Path.of(properties.auditFile()).toAbsolutePath();
        if (path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }
        return new AsyncAuditSink(new FileAuditSink(path));
    }

    /**
     * The unary turn agent, wrapped in {@link IdempotentAgent} so a retried request carrying the same
     * {@code Idempotency-Key} (passed through by {@link AgentController}) returns the prior result instead
     * of re-running the model and effectful tools. Only non-retryable outcomes are cached, so a transient
     * failure still re-runs. The streaming agent is intentionally left unwrapped — replaying a cached
     * result can't reproduce a token stream, so SSE turns are not deduplicated.
     */
    @Bean
    Agent productionAgent(
            ModelPort model,
            ConversationStore conversations,
            AsyncAuditSink audit,
            MeterRegistry meterRegistry,
            AgentProperties properties,
            IdempotencyStore idempotencyStore) {
        Agent base = baseBuilder(model, conversations, audit, meterRegistry, properties).build();
        return new IdempotentAgent(base, idempotencyStore);
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
                .observer(new MicrometerAgentObserver(meterRegistry))
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
    ModelEndpointHealthIndicator model(AgentProperties properties) {
        return new ModelEndpointHealthIndicator(properties.ollamaBaseUrl());
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

    /**
     * Surfaces insecure-by-default configuration. Under the {@code prod} profile this <b>fails startup</b>
     * (an insecure prod deployment should not come up); otherwise it logs loud warnings for local/dev use.
     */
    @Bean
    ApplicationRunner configurationWarnings(
            AgentProperties properties,
            Environment environment,
            @Value("${spring.datasource.password:}") String dbPassword) {
        return args -> {
            List<String> problems = new ArrayList<>();
            if (properties.apiKeys().isEmpty()) {
                problems.add("no agent.api-keys configured — /api would be UNAUTHENTICATED");
            }
            if ("agent".equals(dbPassword)) {
                problems.add("the datasource is using the insecure default password 'agent'");
            }
            if (!properties.hasGuardModel()) {
                problems.add("no agent.guard-model configured — only the crisis + PII guardrails are active");
            }
            if (problems.isEmpty()) {
                return;
            }
            if (environment.matchesProfiles("prod")) {
                throw new IllegalStateException("refusing to start under the 'prod' profile with insecure "
                        + "configuration: " + String.join("; ", problems) + ". Set agent.api-keys, "
                        + "DATABASE_PASSWORD, and agent.guard-model — or run without the 'prod' profile.");
            }
            problems.forEach(p -> log.warn("SECURITY/SAFETY: {} (this fails startup under the 'prod' profile)", p));
        };
    }
}
