package dev.vaijanath.aiagent.fincopilot.auth;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;

/**
 * Authenticates a request by resolving an {@code Authorization: Bearer <token>} session to a user id,
 * which it exposes as the {@link #PRINCIPAL_ATTRIBUTE} request attribute for downstream controllers.
 * A missing or invalid session is rejected with {@code 401}. Registered only for {@code /api/chat/*};
 * the auth endpoints stay public.
 */
public final class SessionAuthenticationFilter implements Filter {

    public static final String PRINCIPAL_ATTRIBUTE = "fincopilot.principal";
    private static final String BEARER = "Bearer ";

    private final AuthService auth;

    public SessionAuthenticationFilter(AuthService auth) {
        this.auth = auth;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest http = (HttpServletRequest) request;
        HttpServletResponse out = (HttpServletResponse) response;
        Optional<String> userId = auth.resolve(bearerToken(http.getHeader("Authorization")));
        if (userId.isEmpty()) {
            out.sendError(HttpServletResponse.SC_UNAUTHORIZED, "authentication required");
            return;
        }
        http.setAttribute(PRINCIPAL_ATTRIBUTE, userId.get());
        chain.doFilter(request, response);
    }

    /** Extracts the token from a {@code Bearer <token>} authorization header, or null. */
    public static String bearerToken(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith(BEARER)) {
            return null;
        }
        return authorizationHeader.substring(BEARER.length()).trim();
    }
}
