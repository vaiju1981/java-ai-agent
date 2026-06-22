package dev.vaijanath.aiagent.reference;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Authenticates API requests against a configured set of keys presented in the {@code X-Api-Key}
 * header. This is a baseline so the reference is not wide-open by default; a real deployment will
 * usually terminate authentication at a gateway and may disable this (configure no keys).
 *
 * <p>When no keys are configured the filter is a pass-through (and {@code AgentConfiguration} logs a
 * loud startup warning). Health probes are always exempt so liveness/readiness stay reachable.
 */
class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    static final String HEADER = "X-Api-Key";

    private final List<byte[]> keys;

    ApiKeyAuthenticationFilter(List<String> apiKeys) {
        this.keys = apiKeys.stream().map(key -> key.getBytes(StandardCharsets.UTF_8)).toList();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // No keys configured -> auth disabled; never gate health probes (k8s / load balancers).
        return keys.isEmpty() || request.getRequestURI().startsWith("/actuator/health");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!authorized(request.getHeader(HEADER))) {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("{\"error\":\"missing or invalid API key\"}");
            return;
        }
        chain.doFilter(request, response);
    }

    private boolean authorized(String presented) {
        if (presented == null || presented.isBlank()) {
            return false;
        }
        byte[] presentedBytes = presented.getBytes(StandardCharsets.UTF_8);
        boolean ok = false;
        for (byte[] key : keys) {
            // Constant-time per key and no early exit, so timing doesn't reveal which key matched.
            ok |= MessageDigest.isEqual(key, presentedBytes);
        }
        return ok;
    }
}
