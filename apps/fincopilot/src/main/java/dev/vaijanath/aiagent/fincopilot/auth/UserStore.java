package dev.vaijanath.aiagent.fincopilot.auth;

import dev.vaijanath.aiagent.store.jdbc.ConnectionSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Raw-JDBC store for {@link User} accounts. Schema is owned by Flyway (V2); this performs no DDL. */
public final class UserStore {

    private final ConnectionSource connections;

    public UserStore(ConnectionSource connections) {
        this.connections = Objects.requireNonNull(connections, "connections");
    }

    /** Inserts a user; returns empty if the email is already registered. */
    public Optional<User> create(String email, String passwordHash) {
        User user = new User(UUID.randomUUID().toString(), email, passwordHash, Instant.now());
        try (Connection c = connections.get();
                PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO fincopilot_users (id, email, password_hash, created_at) "
                                + "VALUES (?, ?, ?, ?) ON CONFLICT (email) DO NOTHING")) {
            ps.setString(1, user.id());
            ps.setString(2, user.email());
            ps.setString(3, user.passwordHash());
            ps.setTimestamp(4, Timestamp.from(user.createdAt()));
            return ps.executeUpdate() == 0 ? Optional.empty() : Optional.of(user);
        } catch (SQLException e) {
            throw new IllegalStateException("failed to create user", e);
        }
    }

    public Optional<User> findByEmail(String email) {
        try (Connection c = connections.get();
                PreparedStatement ps = c.prepareStatement(
                        "SELECT id, email, password_hash, created_at FROM fincopilot_users WHERE email = ?")) {
            ps.setString(1, email);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(new User(
                        rs.getString("id"),
                        rs.getString("email"),
                        rs.getString("password_hash"),
                        rs.getTimestamp("created_at").toInstant()));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("failed to load user", e);
        }
    }
}
