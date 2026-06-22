package dev.vaijanath.aiagent.fincopilot.ledger;

import dev.vaijanath.aiagent.store.jdbc.ConnectionSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Raw-JDBC store for {@link Transaction}s. Schema is owned by Flyway (V3); this performs no DDL. */
public final class TransactionStore {

    private static final String INSERT = "INSERT INTO fincopilot_transactions "
            + "(id, user_id, account_id, txn_date, amount, merchant, category, description, created_at) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";

    // Constant SQL with nullable date bounds (CAST(? AS date) IS NULL -> no filter) — no dynamic SQL.
    private static final String SELECT = "SELECT id, user_id, account_id, txn_date, amount, merchant, "
            + "category, description FROM fincopilot_transactions WHERE user_id = ? "
            + "AND (CAST(? AS date) IS NULL OR txn_date >= CAST(? AS date)) "
            + "AND (CAST(? AS date) IS NULL OR txn_date <= CAST(? AS date)) "
            + "ORDER BY txn_date DESC, id";

    private final ConnectionSource connections;

    public TransactionStore(ConnectionSource connections) {
        this.connections = Objects.requireNonNull(connections, "connections");
    }

    public void add(Transaction transaction) {
        try (Connection c = connections.get();
                PreparedStatement ps = c.prepareStatement(INSERT)) {
            bind(ps, transaction);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("failed to add transaction", e);
        }
    }

    /** Batch-inserts transactions (e.g. a CSV import); returns the number stored. */
    public int addAll(List<Transaction> transactions) {
        if (transactions.isEmpty()) {
            return 0;
        }
        try (Connection c = connections.get();
                PreparedStatement ps = c.prepareStatement(INSERT)) {
            for (Transaction transaction : transactions) {
                bind(ps, transaction);
                ps.addBatch();
            }
            return ps.executeBatch().length;
        } catch (SQLException e) {
            throw new IllegalStateException("failed to import transactions", e);
        }
    }

    /** Lists a user's transactions, newest first, optionally bounded by {@code from}/{@code to} (nullable). */
    public List<Transaction> listByUser(String userId, LocalDate from, LocalDate to) {
        List<Transaction> out = new ArrayList<>();
        try (Connection c = connections.get();
                PreparedStatement ps = c.prepareStatement(SELECT)) {
            ps.setString(1, userId);
            setDate(ps, 2, from);
            setDate(ps, 3, from);
            setDate(ps, 4, to);
            setDate(ps, 5, to);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new Transaction(
                            rs.getString("id"),
                            rs.getString("user_id"),
                            rs.getString("account_id"),
                            rs.getObject("txn_date", LocalDate.class),
                            rs.getBigDecimal("amount"),
                            rs.getString("merchant"),
                            rs.getString("category"),
                            rs.getString("description")));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("failed to list transactions", e);
        }
        return out;
    }

    private static void bind(PreparedStatement ps, Transaction t) throws SQLException {
        ps.setString(1, t.id());
        ps.setString(2, t.userId());
        ps.setString(3, t.accountId());
        ps.setObject(4, t.date());
        ps.setBigDecimal(5, t.amount());
        ps.setString(6, t.merchant());
        ps.setString(7, t.category());
        ps.setString(8, t.description());
        ps.setTimestamp(9, Timestamp.from(Instant.now()));
    }

    private static void setDate(PreparedStatement ps, int index, LocalDate date) throws SQLException {
        if (date == null) {
            ps.setNull(index, Types.DATE);
        } else {
            ps.setObject(index, date);
        }
    }
}
