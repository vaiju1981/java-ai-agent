package dev.vaijanath.aiagent.fincopilot.goals;

import dev.vaijanath.aiagent.store.jdbc.ConnectionSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
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
import java.util.UUID;

/** Raw-JDBC store for {@link SavingsGoal}s. Schema is owned by Flyway (V5); this performs no DDL. */
public final class SavingsGoalStore {

    private final ConnectionSource connections;

    public SavingsGoalStore(ConnectionSource connections) {
        this.connections = Objects.requireNonNull(connections, "connections");
    }

    public SavingsGoal create(String userId, String name, BigDecimal targetAmount, LocalDate targetDate) {
        SavingsGoal goal =
                new SavingsGoal(UUID.randomUUID().toString(), userId, name, targetAmount, targetDate, Instant.now());
        try (Connection c = connections.get();
                PreparedStatement ps = c.prepareStatement("INSERT INTO fincopilot_goals "
                        + "(id, user_id, name, target_amount, target_date, created_at) VALUES (?, ?, ?, ?, ?, ?)")) {
            ps.setString(1, goal.id());
            ps.setString(2, goal.userId());
            ps.setString(3, goal.name());
            ps.setBigDecimal(4, goal.targetAmount());
            if (goal.targetDate() == null) {
                ps.setNull(5, Types.DATE);
            } else {
                ps.setDate(5, Date.valueOf(goal.targetDate()));
            }
            ps.setTimestamp(6, Timestamp.from(goal.createdAt()));
            ps.executeUpdate();
            return goal;
        } catch (SQLException e) {
            throw new IllegalStateException("failed to create savings goal", e);
        }
    }

    public List<SavingsGoal> listByUser(String userId) {
        List<SavingsGoal> out = new ArrayList<>();
        try (Connection c = connections.get();
                PreparedStatement ps = c.prepareStatement("SELECT id, user_id, name, target_amount, "
                        + "target_date, created_at FROM fincopilot_goals WHERE user_id = ? ORDER BY created_at")) {
            ps.setString(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Date date = rs.getDate("target_date");
                    out.add(new SavingsGoal(
                            rs.getString("id"),
                            rs.getString("user_id"),
                            rs.getString("name"),
                            rs.getBigDecimal("target_amount"),
                            date == null ? null : date.toLocalDate(),
                            rs.getTimestamp("created_at").toInstant()));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("failed to list savings goals", e);
        }
        return out;
    }
}
