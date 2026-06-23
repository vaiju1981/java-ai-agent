package dev.vaijanath.aiagent.fincopilot.me;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vaijanath.aiagent.fincopilot.auth.SessionAuthenticationFilter;
import dev.vaijanath.aiagent.fincopilot.auth.UserStore;
import dev.vaijanath.aiagent.fincopilot.ledger.AccountStore;
import dev.vaijanath.aiagent.fincopilot.ledger.Transaction;
import dev.vaijanath.aiagent.fincopilot.ledger.TransactionStore;
import dev.vaijanath.aiagent.store.jdbc.ConnectionSource;
import java.math.BigDecimal;
import java.sql.DriverManager;
import java.time.LocalDate;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Usage metering, the per-user quota filter, and data-subject export/delete against a real Postgres
 * (Testcontainers, skipped without Docker).
 */
@Testcontainers(disabledWithoutDocker = true)
class MeIntegrationTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg17").asCompatibleSubstituteFor("postgres"));

    private static ConnectionSource connections;
    private static AccountStore accounts;
    private static TransactionStore transactions;
    private static UsageMeter usage;
    private static UserDataService data;

    @BeforeAll
    static void setup() {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .load()
                .migrate();
        connections = () ->
                DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        accounts = new AccountStore(connections);
        transactions = new TransactionStore(connections);
        usage = new UsageMeter(connections);
        data = new UserDataService(connections, accounts, transactions);
    }

    @Test
    void metersDailyRequests() {
        String user = freshUser("meter@example.com");
        assertEquals(1, usage.record(user));
        assertEquals(2, usage.record(user));
        assertEquals(2, usage.today(user));
    }

    @Test
    void exportReturnsTheUsersData() {
        String user = freshUser("export@example.com");
        seedOneTransaction(user);
        UserDataService.Export export = data.export(user);
        assertEquals(1, export.accounts().size());
        assertEquals(1, export.transactions().size());
    }

    @Test
    void deleteRemovesAllUserData() {
        String user = freshUser("delete@example.com");
        seedOneTransaction(user);
        usage.record(user);

        data.delete(user);

        assertTrue(accounts.listByUser(user).isEmpty(), "accounts gone");
        assertTrue(transactions.listByUser(user, null, null).isEmpty(), "transactions gone");
        assertEquals(0, usage.today(user), "usage gone");
        assertTrue(new UserStore(connections).findByEmail("delete@example.com").isEmpty(), "user gone");
    }

    @Test
    void quotaFilterRejectsRequestsOverTheDailyLimit() throws Exception {
        String user = freshUser("quota@example.com");
        QuotaFilter filter = new QuotaFilter(usage, 1);

        MockFilterChain firstChain = new MockFilterChain();
        filter.doFilter(authed(user), new MockHttpServletResponse(), firstChain);
        assertNotNull(firstChain.getRequest(), "first request (within quota) passes through");

        MockFilterChain secondChain = new MockFilterChain();
        MockHttpServletResponse rejected = new MockHttpServletResponse();
        filter.doFilter(authed(user), rejected, secondChain);
        assertEquals(429, rejected.getStatus(), "second request is over quota");
        assertNull(secondChain.getRequest(), "rejected request does not reach the chain");
    }

    private static MockHttpServletRequest authed(String userId) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(SessionAuthenticationFilter.PRINCIPAL_ATTRIBUTE, userId);
        return request;
    }

    private static void seedOneTransaction(String userId) {
        String accountId = accounts.create(userId, "Checking", "checking", "USD").id();
        transactions.add(new Transaction(
                UUID.randomUUID().toString(),
                userId,
                accountId,
                LocalDate.parse("2026-01-01"),
                new BigDecimal("-10.00"),
                "Store",
                "groceries",
                ""));
    }

    private static String freshUser(String email) {
        return new UserStore(connections).create(email, "hash").orElseThrow().id();
    }
}
