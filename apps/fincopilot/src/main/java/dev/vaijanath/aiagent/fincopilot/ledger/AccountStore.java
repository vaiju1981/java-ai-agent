package dev.vaijanath.aiagent.fincopilot.ledger;

import dev.vaijanath.aiagent.store.jdbc.ConnectionSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Raw-JDBC store for {@link Account}s. Schema is owned by Flyway (V3); this performs no DDL. */
public final class AccountStore {

    private final ConnectionSource connections;

    public AccountStore(ConnectionSource connections) {
        this.connections = Objects.requireNonNull(connections, "connections");
    }

    public Account create(String userId, String name, String type, String currency) {
        Account account =
                new Account(UUID.randomUUID().toString(), userId, name, type, currency, Instant.now());
        try (Connection c = connections.get();
                PreparedStatement ps = c.prepareStatement("INSERT INTO fincopilot_accounts "
                        + "(id, user_id, name, type, currency, created_at) VALUES (?, ?, ?, ?, ?, ?)")) {
            ps.setString(1, account.id());
            ps.setString(2, account.userId());
            ps.setString(3, account.name());
            ps.setString(4, account.type());
            ps.setString(5, account.currency());
            ps.setTimestamp(6, Timestamp.from(account.createdAt()));
            ps.executeUpdate();
            return account;
        } catch (SQLException e) {
            throw new IllegalStateException("failed to create account", e);
        }
    }

    public List<Account> listByUser(String userId) {
        List<Account> out = new ArrayList<>();
        try (Connection c = connections.get();
                PreparedStatement ps = c.prepareStatement("SELECT id, user_id, name, type, currency, "
                        + "created_at FROM fincopilot_accounts WHERE user_id = ? ORDER BY created_at")) {
            ps.setString(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new Account(
                            rs.getString("id"),
                            rs.getString("user_id"),
                            rs.getString("name"),
                            rs.getString("type"),
                            rs.getString("currency"),
                            rs.getTimestamp("created_at").toInstant()));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("failed to list accounts", e);
        }
        return out;
    }

    /** True if {@code accountId} exists and belongs to {@code userId} (the ownership guard for writes). */
    public boolean existsForUser(String userId, String accountId) {
        try (Connection c = connections.get();
                PreparedStatement ps = c.prepareStatement(
                        "SELECT 1 FROM fincopilot_accounts WHERE id = ? AND user_id = ?")) {
            ps.setString(1, accountId);
            ps.setString(2, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("failed to check account ownership", e);
        }
    }
}
