package dev.vaijanath.aiagent.reference;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;
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
    /** Request attribute the filter sets to the authenticated key's tenant (read by the controller). */
    static final String TENANT_ATTRIBUTE = "agent.authenticatedTenant";

    private final List<Map.Entry<byte[], String>> keys; // (key bytes -> bound tenant)

    ApiKeyAuthenticationFilter(Map<String, String> apiKeys) {
        this.keys = apiKeys.entrySet().stream()
                .map(e -> Map.entry(e.getKey().getBytes(StandardCharsets.UTF_8), e.getValue()))
                .toList();
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
        String tenant = resolveTenant(request.getHeader(HEADER));
        if (tenant == null) {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("{\"error\":\"missing or invalid API key\"}");
            return;
        }
        // Bind the tenant to the credential — the controller uses this, not a client-supplied header.
        request.setAttribute(TENANT_ATTRIBUTE, tenant);
        chain.doFilter(request, response);
    }

    /** The tenant bound to the presented key, or {@code null} if it matches none. Constant-time per key. */
    private String resolveTenant(String presented) {
        if (presented == null || presented.isBlank()) {
            return null;
        }
        byte[] presentedBytes = presented.getBytes(StandardCharsets.UTF_8);
        String tenant = null;
        for (Map.Entry<byte[], String> entry : keys) {
            // Compare every key with no early exit, so timing doesn't reveal which key matched.
            tenant = MessageDigest.isEqual(entry.getKey(), presentedBytes) ? entry.getValue() : tenant;
        }
        return tenant;
    }
}
