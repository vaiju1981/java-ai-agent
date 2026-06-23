package dev.vaijanath.aiagent.fincopilot.analyst;

import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vaijanath.aiagent.agent.Agent;
import dev.vaijanath.aiagent.agent.AgentRequest;
import dev.vaijanath.aiagent.agent.AgentResponse;
import dev.vaijanath.aiagent.agent.ProductionAgentRuntime;
import dev.vaijanath.aiagent.agent.RequestContext;
import dev.vaijanath.aiagent.audit.InMemoryAuditSink;
import dev.vaijanath.aiagent.fincopilot.auth.UserStore;
import dev.vaijanath.aiagent.fincopilot.ledger.AccountStore;
import dev.vaijanath.aiagent.fincopilot.ledger.Transaction;
import dev.vaijanath.aiagent.fincopilot.ledger.TransactionStore;
import dev.vaijanath.aiagent.langchain4j.OllamaModelPorts;
import dev.vaijanath.aiagent.memory.InMemoryConversationStore;
import dev.vaijanath.aiagent.model.ModelPort;
import dev.vaijanath.aiagent.store.jdbc.ConnectionSource;
import dev.vaijanath.aiagent.tools.jsonschema.JsonSchemaToolValidator;
import java.math.BigDecimal;
import java.sql.DriverManager;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * The Analyst end-to-end: a real model (gemma4:31b-cloud via Ollama) drives the READ_ONLY tools over
 * real transactions in a real Postgres to answer a finance question. Gated on Docker AND
 * {@code OLLAMA_TOOLCALL_PROBE}, so it runs only where both are available.
 */
@Testcontainers(disabledWithoutDocker = true)
class AnalystLiveE2eTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg17").asCompatibleSubstituteFor("postgres"));

    private static Analytics analytics;
    private static String userId;

    @BeforeAll
    static void seed() {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .load()
                .migrate();
        ConnectionSource connections = () ->
                DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        userId = new UserStore(connections).create("analyst-e2e@example.com", "hash").orElseThrow().id();
        String accountId = new AccountStore(connections).create(userId, "Checking", "checking", "USD").id();
        TransactionStore store = new TransactionStore(connections);
        store.addAll(List.of(
                txn(accountId, "2026-01-05", "-100.00", "Whole Foods", "groceries"),
                txn(accountId, "2026-01-12", "-50.00", "Trader Joe", "groceries"),
                txn(accountId, "2026-02-03", "-60.00", "Whole Foods", "groceries"),
                txn(accountId, "2026-01-20", "-40.00", "Diner", "dining"),
                txn(accountId, "2026-01-31", "3000.00", "Payroll", "income")));
        analytics = new Analytics(store);
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "OLLAMA_TOOLCALL_PROBE", matches = ".+")
    void analystAnswersFromRealTransactions() {
        String baseUrl = System.getenv().getOrDefault("OLLAMA_BASE_URL", "http://localhost:11434");
        String modelName = System.getenv().getOrDefault("OLLAMA_MODEL", "gemma4:31b-cloud");
        ModelPort model = OllamaModelPorts.ollama(baseUrl, modelName);

        Agent agent = ProductionAgentRuntime.builder()
                .model(model)
                .conversationStore(new InMemoryConversationStore())
                .auditSink(new InMemoryAuditSink())
                .argumentValidator(new JsonSchemaToolValidator())
                .tool(new AnalystTools.FinanceSummaryTool(analytics))
                .tool(new AnalystTools.SpendingByCategoryTool(analytics))
                .tool(new AnalystTools.MonthlyCashflowTool(analytics))
                .modelTimeout(Duration.ofSeconds(120))
                .toolTimeout(Duration.ofSeconds(20))
                .maxSteps(6)
                .systemPrompt("You are a finance assistant. Use the provided tools to answer questions about "
                        + "the user's finances; never guess numbers. State the figure you find.")
                .build();

        // principal = userId so the ContextualTools scope to this user's data.
        RequestContext context = new RequestContext(
                "session", userId, userId, "trace", Instant.now().plus(Duration.ofMinutes(2)), Map.of());
        AgentResponse response = agent.run(new AgentRequest("How much did I spend on groceries in total?", context));

        System.out.println("[analyst] " + response.output());
        assertTrue(response.output().contains("210"), "answer should reflect the 210.00 grocery total: " + response.output());
    }

    private static Transaction txn(String accountId, String date, String amount, String merchant, String category) {
        return new Transaction(
                UUID.randomUUID().toString(),
                userId,
                accountId,
                LocalDate.parse(date),
                new BigDecimal(amount),
                merchant,
                category,
                "");
    }
}
