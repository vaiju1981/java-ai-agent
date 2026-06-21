package dev.vaijanath.aiagent.reference;

import dev.vaijanath.aiagent.agent.Agent;
import dev.vaijanath.aiagent.agent.ProductionAgentRuntime;
import dev.vaijanath.aiagent.audit.AsyncAuditSink;
import dev.vaijanath.aiagent.audit.FileAuditSink;
import dev.vaijanath.aiagent.langchain4j.OllamaModelPorts;
import dev.vaijanath.aiagent.memory.ConversationStore;
import dev.vaijanath.aiagent.model.ModelPort;
import dev.vaijanath.aiagent.store.jdbc.JdbcConversationStore;
import dev.vaijanath.aiagent.tools.jsonschema.JsonSchemaToolValidator;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.sql.DataSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(AgentProperties.class)
class AgentConfiguration {

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
            AgentProperties properties) {
        return ProductionAgentRuntime.builder()
                .model(model)
                .conversationStore(conversations)
                .auditSink(audit)
                .argumentValidator(new JsonSchemaToolValidator())
                .modelTimeout(properties.modelTimeout())
                .toolTimeout(properties.toolTimeout())
                .systemPrompt("You are a concise, accurate production assistant. Never invent tool results.")
                .build();
    }
}
