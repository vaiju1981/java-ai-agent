package dev.vaijanath.aiagent.demos;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;

final class OperationsDatabase implements AutoCloseable {

    private final Connection connection;

    private OperationsDatabase(Connection connection) {
        this.connection = connection;
    }

    static OperationsDatabase open(Path file) {
        try {
            Connection connection = DriverManager.getConnection("jdbc:sqlite:" + file.toAbsolutePath());
            connection.createStatement().execute("PRAGMA foreign_keys = ON");
            OperationsDatabase database = new OperationsDatabase(connection);
            database.migrate();
            database.seed();
            return database;
        } catch (SQLException e) {
            throw new IllegalStateException("failed to open demo database", e);
        }
    }

    String account(String accountId) {
        recordToolInvocation("account_lookup", "READ_ONLY", "{\"accountId\":\"" + accountId + "\"}");
        return queryOne("""
                select json_object(
                  'accountId', account_id,
                  'name', name,
                  'standing', standing,
                  'supportTier', support_tier,
                  'risk', risk
                )
                from accounts where account_id = ?
                """, accountId);
    }

    String invoice(String invoiceId) {
        recordToolInvocation("invoice_lookup", "READ_ONLY", "{\"invoiceId\":\"" + invoiceId + "\"}");
        return queryOne("""
                select json_object(
                  'invoiceId', invoice_id,
                  'accountId', account_id,
                  'status', status,
                  'amountCents', amount_cents,
                  'shipmentDelayDays', shipment_delay_days,
                  'openDisputes', open_disputes
                )
                from invoices where invoice_id = ?
                """, invoiceId);
    }

    String policy(String policyKey) {
        recordToolInvocation("policy_lookup", "READ_ONLY", "{\"policy\":\"" + policyKey + "\"}");
        return queryOne("""
                select json_object(
                  'policy', policy_key,
                  'maxAutomaticCreditCents', max_automatic_credit_cents,
                  'requiresHumanApprovalForRefund', requires_human_approval_for_refund,
                  'guidance', guidance
                )
                from policies where policy_key = ?
                """, policyKey);
    }

    synchronized String issueRefund(String accountId, String invoiceId, int amountCents, String idempotencyKey) {
        try {
            connection.setAutoCommit(false);
            recordToolInvocation(
                    "issue_refund",
                    "EFFECTFUL",
                    "{\"accountId\":\"" + accountId + "\",\"invoiceId\":\"" + invoiceId
                            + "\",\"amountCents\":" + amountCents + "}");
            try (PreparedStatement insert = connection.prepareStatement("""
                    insert or ignore into refunds(
                      refund_id, account_id, invoice_id, amount_cents, idempotency_key, status, created_at
                    ) values (?, ?, ?, ?, ?, 'issued', ?)
                    """)) {
                insert.setString(1, "rfnd-" + Integer.toHexString(idempotencyKey.hashCode()));
                insert.setString(2, accountId);
                insert.setString(3, invoiceId);
                insert.setInt(4, amountCents);
                insert.setString(5, idempotencyKey);
                insert.setString(6, Instant.now().toString());
                insert.executeUpdate();
            }
            String refund = queryOne("""
                    select json_object(
                      'refundId', refund_id,
                      'accountId', account_id,
                      'invoiceId', invoice_id,
                      'amountCents', amount_cents,
                      'status', status,
                      'idempotencyKey', idempotency_key
                    )
                    from refunds where idempotency_key = ?
                    """, idempotencyKey);
            connection.commit();
            return refund;
        } catch (RuntimeException | SQLException e) {
            rollbackQuietly();
            throw new IllegalStateException("failed to issue refund", e);
        } finally {
            restoreAutoCommitQuietly();
        }
    }

    private void restoreAutoCommitQuietly() {
        try {
            connection.setAutoCommit(true);
        } catch (SQLException ignored) {
            // best-effort: the refund above already committed or rolled back; throwing here would
            // mask that outcome. A failed restore surfaces on the connection's next use instead.
        }
    }

    int readOnlyToolInvocationCount() {
        return count("select count(*) from tool_invocations where effect = 'READ_ONLY'");
    }

    int effectfulToolInvocationCount() {
        return count("select count(*) from tool_invocations where effect = 'EFFECTFUL'");
    }

    int refundCount() {
        return count("select count(*) from refunds");
    }

    @Override
    public void close() {
        try {
            connection.close();
        } catch (SQLException e) {
            throw new IllegalStateException("failed to close demo database", e);
        }
    }

    private void migrate() throws SQLException {
        try (Statement st = connection.createStatement()) {
            st.executeUpdate("""
                    create table if not exists accounts (
                      account_id text primary key,
                      name text not null,
                      standing text not null,
                      support_tier text not null,
                      risk text not null
                    )
                    """);
            st.executeUpdate("""
                    create table if not exists invoices (
                      invoice_id text primary key,
                      account_id text not null references accounts(account_id),
                      status text not null,
                      amount_cents integer not null,
                      shipment_delay_days integer not null,
                      open_disputes integer not null
                    )
                    """);
            st.executeUpdate("""
                    create table if not exists policies (
                      policy_key text primary key,
                      max_automatic_credit_cents integer not null,
                      requires_human_approval_for_refund integer not null,
                      guidance text not null
                    )
                    """);
            st.executeUpdate("""
                    create table if not exists refunds (
                      refund_id text primary key,
                      account_id text not null references accounts(account_id),
                      invoice_id text not null references invoices(invoice_id),
                      amount_cents integer not null,
                      idempotency_key text not null unique,
                      status text not null,
                      created_at text not null
                    )
                    """);
            st.executeUpdate("""
                    create table if not exists tool_invocations (
                      id integer primary key autoincrement,
                      tool_name text not null,
                      effect text not null,
                      arguments_json text not null,
                      created_at text not null
                    )
                    """);
        }
    }

    private void seed() throws SQLException {
        try (Statement st = connection.createStatement()) {
            st.executeUpdate("""
                    insert or ignore into accounts(account_id, name, standing, support_tier, risk)
                    values ('acct-2042', 'Acme Outdoor Supply', 'good', 'growth', 'low')
                    """);
            st.executeUpdate("""
                    insert or ignore into invoices(
                      invoice_id, account_id, status, amount_cents, shipment_delay_days, open_disputes
                    ) values ('INV-771', 'acct-2042', 'paid', 184000, 3, 0)
                    """);
            st.executeUpdate("""
                    insert or ignore into policies(
                      policy_key, max_automatic_credit_cents, requires_human_approval_for_refund, guidance
                    ) values (
                      'courtesy-credit',
                      5000,
                      1,
                      'Offer a courtesy credit after delayed shipment; do not move money without approval.'
                    )
                    """);
        }
    }

    private void recordToolInvocation(String toolName, String effect, String argumentsJson) {
        try (PreparedStatement ps = connection.prepareStatement("""
                insert into tool_invocations(tool_name, effect, arguments_json, created_at)
                values (?, ?, ?, ?)
                """)) {
            ps.setString(1, toolName);
            ps.setString(2, effect);
            ps.setString(3, argumentsJson);
            ps.setString(4, Instant.now().toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("failed to record tool invocation", e);
        }
    }

    private String queryOne(String sql, String value) {
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, value);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return "{\"error\":\"not found\"}";
                }
                return rs.getString(1);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("database query failed", e);
        }
    }

    private int count(String sql) {
        try (Statement st = connection.createStatement();
                ResultSet rs = st.executeQuery(sql)) {
            return rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException e) {
            throw new IllegalStateException("database count failed", e);
        }
    }

    private void rollbackQuietly() {
        try {
            connection.rollback();
        } catch (SQLException ignored) {
            // Keep the original failure.
        }
    }
}
