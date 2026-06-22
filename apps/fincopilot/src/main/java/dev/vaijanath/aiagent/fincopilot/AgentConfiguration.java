package dev.vaijanath.aiagent.fincopilot;

import dev.vaijanath.aiagent.agent.Agent;
import dev.vaijanath.aiagent.agent.ProductionAgentRuntime;
import dev.vaijanath.aiagent.audit.AsyncAuditSink;
import dev.vaijanath.aiagent.audit.FileAuditSink;
import dev.vaijanath.aiagent.guardrail.CrisisGuardrail;
import dev.vaijanath.aiagent.guardrail.PiiScrubGuardrail;
import dev.vaijanath.aiagent.langchain4j.OllamaModelPorts;
import dev.vaijanath.aiagent.memory.ConversationStore;
import dev.vaijanath.aiagent.model.ModelPort;
import dev.vaijanath.aiagent.observe.AgentObserver;
import dev.vaijanath.aiagent.store.jdbc.JdbcConversationStore;
import dev.vaijanath.aiagent.tools.jsonschema.JsonSchemaToolValidator;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;
import javax.sql.DataSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the governed FinCopilot agent on the Ollama substrate. M0 is a single agent with no domain
 * tools (the "empty" walking skeleton); the Analyst tools and the supervisor/Advisor split arrive in
 * M1/M2. Crisis + PII guardrails always run; schema is owned by Flyway (the agent-store-jdbc migration).
 */
@Configuration
@EnableConfigurationProperties(FinCopilotProperties.class)
class AgentConfiguration {

    @Bean
    ModelPort modelPort(FinCopilotProperties properties) {
        return OllamaModelPorts.ollama(properties.ollamaBaseUrl(), properties.model());
    }

    @Bean
    ConversationStore conversationStore(DataSource dataSource, FinCopilotProperties properties) {
        // Flyway owns schema (V1 ships in agent-store-jdbc); this constructor performs no DDL.
        return new JdbcConversationStore(dataSource::getConnection, properties.historyTurns());
    }

    @Bean(destroyMethod = "close")
    AsyncAuditSink auditSink(FinCopilotProperties properties) throws IOException {
        Path path = Path.of(properties.auditFile()).toAbsolutePath();
        if (path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }
        return new AsyncAuditSink(new FileAuditSink(path));
    }

    @Bean
    Agent financeAgent(
            ModelPort model,
            ConversationStore conversations,
            AsyncAuditSink audit,
            FinCopilotProperties properties) {
        return baseBuilder(model, conversations, audit, properties).build();
    }

    /**
     * A per-request agent carrying an extra {@link AgentObserver} for the streaming endpoint, so turn
     * events reach one SSE client without leaking across concurrent requests.
     */
    @Bean
    Function<AgentObserver, Agent> streamingAgentFactory(
            ModelPort model,
            ConversationStore conversations,
            AsyncAuditSink audit,
            FinCopilotProperties properties) {
        return observer ->
                baseBuilder(model, conversations, audit, properties).observer(observer).build();
    }

    /** Runs streaming turns off the request thread so the SSE response returns immediately. */
    @Bean(destroyMethod = "close")
    ExecutorService streamExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    private ProductionAgentRuntime.Builder baseBuilder(
            ModelPort model,
            ConversationStore conversations,
            AsyncAuditSink audit,
            FinCopilotProperties properties) {
        return ProductionAgentRuntime.builder()
                .model(model)
                .conversationStore(conversations)
                .auditSink(audit)
                .argumentValidator(new JsonSchemaToolValidator())
                .modelTimeout(properties.modelTimeout())
                .toolTimeout(properties.toolTimeout())
                .guardrail(new CrisisGuardrail())
                .guardrail(new PiiScrubGuardrail())
                .systemPrompt("You are FinCopilot, a careful, grounded finance assistant for individuals and "
                        + "small businesses. You provide information and analysis, not regulated financial, "
                        + "investment, or tax advice. Never invent numbers or tool results; if you do not have "
                        + "the data, say so.");
    }
}
