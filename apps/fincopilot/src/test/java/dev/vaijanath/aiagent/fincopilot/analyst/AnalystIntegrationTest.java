package dev.vaijanath.aiagent.fincopilot.analyst;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vaijanath.aiagent.fincopilot.auth.UserStore;
import dev.vaijanath.aiagent.fincopilot.ledger.AccountStore;
import dev.vaijanath.aiagent.fincopilot.ledger.Transaction;
import dev.vaijanath.aiagent.fincopilot.ledger.TransactionStore;
import dev.vaijanath.aiagent.model.ToolCall;
import dev.vaijanath.aiagent.store.jdbc.ConnectionSource;
import dev.vaijanath.aiagent.tool.ContextualTool;
import dev.vaijanath.aiagent.tool.ToolCallContext;
import dev.vaijanath.aiagent.tool.ToolInvocation;
import dev.vaijanath.aiagent.tool.ToolSpec;
import java.math.BigDecimal;
import java.sql.DriverManager;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * The Analyst's aggregations and READ_ONLY tools over seeded transactions in a real Postgres
 * (Testcontainers, skipped without Docker), including that the tools scope to the invocation's user.
 */
@Testcontainers(disabledWithoutDocker = true)
class AnalystIntegrationTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg17").asCompatibleSubstituteFor("postgres"));

    private static Analytics analytics;
    private static String userId;
    private static String accountId;

    @BeforeAll
    static void seed() {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .load()
                .migrate();
        ConnectionSource connections = () ->
                DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        userId = new UserStore(connections).create("analyst@example.com", "hash").orElseThrow().id();
        accountId = new AccountStore(connections).create(userId, "Checking", "checking", "USD").id();
        TransactionStore store = new TransactionStore(connections);
        store.addAll(List.of(
                txn("2026-01-05", "-100.00", "Whole Foods", "groceries"),
                txn("2026-01-12", "-50.00", "Trader Joe", "groceries"),
                txn("2026-01-20", "-40.00", "Diner", "dining"),
                txn("2026-01-31", "3000.00", "Payroll", "income"),
                txn("2026-02-03", "-60.00", "Whole Foods", "groceries"),
                txn("2026-02-28", "3000.00", "Payroll", "income")));
        analytics = new Analytics(store);
    }

    @Test
    void aggregatesSpendingByCategory() {
        List<Analytics.CategorySpend> spend = analytics.spendingByCategory(userId, null, null);
        assertEquals("groceries", spend.get(0).category(), "groceries is the largest expense category");
        assertEquals(new BigDecimal("210.00"), spend.get(0).spent());
    }

    @Test
    void summarizesIncomeExpenseAndNet() {
        Analytics.Summary s = analytics.summary(userId);
        assertEquals(6, s.transactionCount());
        assertEquals(new BigDecimal("6000.00"), s.totalIncome());
        assertEquals(new BigDecimal("250.00"), s.totalExpense());
        assertEquals(new BigDecimal("5750.00"), s.net());
    }

    @Test
    void splitsCashflowByMonth() {
        List<Analytics.MonthFlow> flow = analytics.monthlyCashflow(userId);
        assertEquals(2, flow.size());
        assertEquals("2026-01", flow.get(0).month());
        assertEquals(new BigDecimal("190.00"), flow.get(0).expense()); // 100 + 50 + 40
    }

    @Test
    void toolsScopeToTheInvocationUserAndFormatResults() {
        ContextualTool summary = new AnalystTools.FinanceSummaryTool(analytics);
        String summaryOut = summary.invoke(invocation(summary.spec(), "{}")).content();
        assertTrue(summaryOut.contains("6000.00"), summaryOut);
        assertTrue(summaryOut.contains("5750.00"), summaryOut);

        ContextualTool byCategory = new AnalystTools.SpendingByCategoryTool(analytics);
        String categoryOut = byCategory.invoke(invocation(byCategory.spec(), "{}")).content();
        assertTrue(categoryOut.contains("groceries: 210.00"), categoryOut);
    }

    private static ToolInvocation invocation(ToolSpec spec, String args) {
        return new ToolInvocation(
                new ToolCall("c1", spec.name(), args),
                new ToolCallContext(spec, args, userId, userId, "trace", "session", Instant.now().plusSeconds(60)));
    }

    private static Transaction txn(String date, String amount, String merchant, String category) {
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
