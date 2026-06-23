package dev.vaijanath.aiagent.fincopilot.me;

import dev.vaijanath.aiagent.store.jdbc.ConnectionSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Objects;

/** Per-user daily request metering, for usage display and quota enforcement. Schema: Flyway V4. */
public final class UsageMeter {

    private final ConnectionSource connections;

    public UsageMeter(ConnectionSource connections) {
        this.connections = Objects.requireNonNull(connections, "connections");
    }

    /** Records one request for the user today and returns the new running count for today. */
    public int record(String userId) {
        try (Connection c = connections.get();
                PreparedStatement ps = c.prepareStatement("INSERT INTO fincopilot_usage "
                        + "(user_id, usage_day, requests) VALUES (?, ?, 1) "
                        + "ON CONFLICT (user_id, usage_day) DO UPDATE SET requests = fincopilot_usage.requests + 1 "
                        + "RETURNING requests")) {
            ps.setString(1, userId);
            ps.setObject(2, LocalDate.now());
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("failed to record usage", e);
        }
    }

    /** Requests recorded for the user today. */
    public int today(String userId) {
        try (Connection c = connections.get();
                PreparedStatement ps = c.prepareStatement(
                        "SELECT requests FROM fincopilot_usage WHERE user_id = ? AND usage_day = ?")) {
            ps.setString(1, userId);
            ps.setObject(2, LocalDate.now());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("failed to read usage", e);
        }
    }
}
