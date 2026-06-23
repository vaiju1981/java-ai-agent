package dev.vaijanath.aiagent.fincopilot.auth;

import java.time.Duration;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Signup, login, and session resolution. Passwords are hashed with the injected {@link PasswordEncoder}
 * (BCrypt in production) and never stored or logged in the clear.
 */
public final class AuthService {

    private final UserStore users;
    private final SessionStore sessions;
    private final PasswordEncoder passwordEncoder;
    private final Duration sessionTtl;
    private final String dummyHash;

    public AuthService(
            UserStore users, SessionStore sessions, PasswordEncoder passwordEncoder, Duration sessionTtl) {
        this.users = Objects.requireNonNull(users, "users");
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.passwordEncoder = Objects.requireNonNull(passwordEncoder, "passwordEncoder");
        this.sessionTtl = Objects.requireNonNull(sessionTtl, "sessionTtl");
        // A throwaway hash so login can always run a comparison (timing-equalised, see login()).
        this.dummyHash = passwordEncoder.encode("unused-account-placeholder");
    }

    /** Registers a new account and returns a fresh session token; empty if the email is already taken. */
    public Optional<String> signup(String email, String password) {
        String hash = passwordEncoder.encode(password);
        return users.create(normalize(email), hash).map(user -> sessions.create(user.id(), sessionTtl));
    }

    /**
     * Returns a fresh session token on valid credentials, else empty — the same response for an unknown
     * email and a wrong password, so the endpoint does not leak which emails are registered.
     */
    public Optional<String> login(String email, String password) {
        Optional<User> user = users.findByEmail(normalize(email));
        // Always run a hash comparison (real or dummy) so response timing doesn't reveal which emails
        // are registered (no user enumeration).
        String hash = user.map(User::passwordHash).orElse(dummyHash);
        boolean passwordMatches = passwordEncoder.matches(password, hash);
        if (user.isEmpty() || !passwordMatches) {
            return Optional.empty();
        }
        return Optional.of(sessions.create(user.get().id(), sessionTtl));
    }

    /** Resolves a session token to its (non-expired) user id, else empty. */
    public Optional<String> resolve(String token) {
        return token == null || token.isBlank() ? Optional.empty() : sessions.resolveUser(token);
    }

    /** Invalidates a session token (a no-op if null/blank/unknown). */
    public void logout(String token) {
        if (token != null && !token.isBlank()) {
            sessions.delete(token);
        }
    }

    private static String normalize(String email) {
        return email == null ? "" : email.strip().toLowerCase(Locale.ROOT);
    }
}
