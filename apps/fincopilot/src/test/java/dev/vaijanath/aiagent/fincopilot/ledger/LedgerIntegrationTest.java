package dev.vaijanath.aiagent.fincopilot.ledger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vaijanath.aiagent.fincopilot.auth.UserStore;
import dev.vaijanath.aiagent.store.jdbc.ConnectionSource;
import java.math.BigDecimal;
import java.sql.DriverManager;
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
 * End-to-end ledger-store test against a real Postgres (Testcontainers, skipped without Docker): account
 * creation + ownership, manual and bulk transaction insert, date-bounded listing, and ordering — over
 * the real Flyway schema.
 */
@Testcontainers(disabledWithoutDocker = true)
class LedgerIntegrationTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg17").asCompatibleSubstituteFor("postgres"));

    private static AccountStore accounts;
    private static TransactionStore transactions;
    private static String userId;

    @BeforeAll
    static void setup() {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .load()
                .migrate();
        ConnectionSource connections = () ->
                DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        userId = new UserStore(connections).create("ledger@example.com", "hash").orElseThrow().id();
        accounts = new AccountStore(connections);
        transactions = new TransactionStore(connections);
    }

    @Test
    void accountOwnershipAndTransactionRoundTrip() {
        Account account = accounts.create(userId, "Checking", "checking", "USD");
        assertTrue(accounts.existsForUser(userId, account.id()));
        assertFalse(accounts.existsForUser(userId, "does-not-exist"));
        assertFalse(accounts.existsForUser("someone-else", account.id()));
        assertEquals(1, accounts.listByUser(userId).size());

        transactions.add(txn(account.id(), "2026-01-15", "-45.99", "Store", "groceries"));
        int imported = transactions.addAll(List.of(
                txn(account.id(), "2026-01-16", "2500.00", "Payroll", "income"),
                txn(account.id(), "2026-01-17", "-12.50", "Coffee", "dining")));
        assertEquals(2, imported);

        List<Transaction> all = transactions.listByUser(userId, null, null);
        assertEquals(3, all.size());
        assertEquals(LocalDate.parse("2026-01-17"), all.get(0).date(), "newest first");

        List<Transaction> fromJan16 = transactions.listByUser(userId, LocalDate.parse("2026-01-16"), null);
        assertEquals(2, fromJan16.size(), "date lower bound applies");
    }

    private Transaction txn(String accountId, String date, String amount, String merchant, String category) {
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
