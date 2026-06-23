package dev.vaijanath.aiagent.fincopilot.me;

import dev.vaijanath.aiagent.fincopilot.ledger.Account;
import dev.vaijanath.aiagent.fincopilot.ledger.AccountStore;
import dev.vaijanath.aiagent.fincopilot.ledger.Transaction;
import dev.vaijanath.aiagent.fincopilot.ledger.TransactionStore;
import dev.vaijanath.aiagent.store.jdbc.ConnectionSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;

/**
 * Data-subject operations (GDPR-style): export and delete all of a user's data. Delete removes the
 * user's conversations (the agent-store-jdbc tables, keyed by tenant = user id), ledger, sessions,
 * usage, and the account row — in foreign-key order, in one transaction.
 */
public final class UserDataService {

    // Constant SQL, foreign-key order; the user id is bound, never formatted in.
    private static final String[] DELETE_STATEMENTS = {
        "DELETE FROM agent_messages WHERE tenant = ?",
        "DELETE FROM agent_turns WHERE tenant = ?",
        "DELETE FROM fincopilot_transactions WHERE user_id = ?",
        "DELETE FROM fincopilot_accounts WHERE user_id = ?",
        "DELETE FROM fincopilot_goals WHERE user_id = ?",
        "DELETE FROM fincopilot_sessions WHERE user_id = ?",
        "DELETE FROM fincopilot_usage WHERE user_id = ?",
        "DELETE FROM fincopilot_users WHERE id = ?",
    };

    private final ConnectionSource connections;
    private final AccountStore accounts;
    private final TransactionStore transactions;

    public UserDataService(ConnectionSource connections, AccountStore accounts, TransactionStore transactions) {
        this.connections = Objects.requireNonNull(connections, "connections");
        this.accounts = Objects.requireNonNull(accounts, "accounts");
        this.transactions = Objects.requireNonNull(transactions, "transactions");
    }

    public record Export(String userId, List<Account> accounts, List<Transaction> transactions) {}

    public Export export(String userId) {
        return new Export(userId, accounts.listByUser(userId), transactions.listByUser(userId, null, null));
    }

    public void delete(String userId) {
        try (Connection c = connections.get()) {
            boolean autoCommit = c.getAutoCommit();
            c.setAutoCommit(false);
            try {
                for (String sql : DELETE_STATEMENTS) {
                    try (PreparedStatement ps = c.prepareStatement(sql)) {
                        ps.setString(1, userId);
                        ps.executeUpdate();
                    }
                }
                c.commit();
            } catch (SQLException e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(autoCommit);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("failed to delete user data", e);
        }
    }
}
