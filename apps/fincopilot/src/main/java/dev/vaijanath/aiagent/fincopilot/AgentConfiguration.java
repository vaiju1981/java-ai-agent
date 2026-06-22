package dev.vaijanath.aiagent.fincopilot;

import dev.vaijanath.aiagent.audit.AsyncAuditSink;
import dev.vaijanath.aiagent.audit.FileAuditSink;
import dev.vaijanath.aiagent.guardrail.CrisisGuardrail;
import dev.vaijanath.aiagent.guardrail.Guardrail;
import dev.vaijanath.aiagent.guardrail.PiiScrubGuardrail;
import dev.vaijanath.aiagent.langchain4j.OllamaModelPorts;
import dev.vaijanath.aiagent.memory.ConversationStore;
import dev.vaijanath.aiagent.model.ModelPort;
import dev.vaijanath.aiagent.store.jdbc.JdbcConversationStore;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.sql.DataSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * FinCopilot's application beans. The governed agent itself — plus the streaming-agent factory and the
 * stream executor — is assembled by the agent-spring-boot-starter from the {@link ModelPort},
 * {@link ConversationStore}, audit sink, and {@link Guardrail} beans declared here; the system prompt,
 * timeouts, and step budget come from {@code agent.*} properties.
 *
 * <p>M0 declares no domain tools (the "empty" walking skeleton); the Analyst tools and the
 * supervisor/Advisor split arrive in M1/M2.
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
    Guardrail crisisGuardrail() {
        return new CrisisGuardrail();
    }

    @Bean
    Guardrail piiScrubGuardrail() {
        return new PiiScrubGuardrail();
    }
}
