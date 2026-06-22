package dev.vaijanath.aiagent.fincopilot.auth;

import dev.vaijanath.aiagent.store.jdbc.ConnectionSource;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Objects;
import java.util.Optional;

/** Raw-JDBC store for opaque server-side sessions. Schema is owned by Flyway (V2). */
public final class SessionStore {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final ConnectionSource connections;

    public SessionStore(ConnectionSource connections) {
        this.connections = Objects.requireNonNull(connections, "connections");
    }

    /** Creates a session for {@code userId} valid for {@code ttl}, returning its opaque token. */
    public String create(String userId, Duration ttl) {
        String token = newToken();
        Instant now = Instant.now();
        try (Connection c = connections.get();
                PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO fincopilot_sessions (token, user_id, created_at, expires_at) "
                                + "VALUES (?, ?, ?, ?)")) {
            ps.setString(1, token);
            ps.setString(2, userId);
            ps.setTimestamp(3, Timestamp.from(now));
            ps.setTimestamp(4, Timestamp.from(now.plus(ttl)));
            ps.executeUpdate();
            return token;
        } catch (SQLException e) {
            throw new IllegalStateException("failed to create session", e);
        }
    }

    /** Returns the user id for a non-expired session, else empty. */
    public Optional<String> resolveUser(String token) {
        try (Connection c = connections.get();
                PreparedStatement ps = c.prepareStatement(
                        "SELECT user_id FROM fincopilot_sessions WHERE token = ? AND expires_at > ?")) {
            ps.setString(1, token);
            ps.setTimestamp(2, Timestamp.from(Instant.now()));
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(rs.getString("user_id")) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("failed to resolve session", e);
        }
    }

    public void delete(String token) {
        try (Connection c = connections.get();
                PreparedStatement ps =
                        c.prepareStatement("DELETE FROM fincopilot_sessions WHERE token = ?")) {
            ps.setString(1, token);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("failed to delete session", e);
        }
    }

    private static String newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
