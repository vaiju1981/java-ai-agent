package dev.vaijanath.aiagent.fincopilot.auth;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A per-IP fixed-window throttle for the public auth endpoints, to blunt credential brute-forcing.
 * Over {@code maxPerMinute} requests from one client address in a calendar minute gets {@code 429}.
 * A limit {@code <= 0} disables it. Behind a proxy, the remote address is the proxy — terminate the
 * real client IP there (or extend this to a trusted {@code X-Forwarded-For}).
 */
public final class AuthThrottleFilter implements Filter {

    private static final int TOO_MANY_REQUESTS = 429;
    private static final int MAX_TRACKED_IPS = 50_000; // crude unbounded-growth guard

    private final int maxPerMinute;
    private final ConcurrentHashMap<String, long[]> windows = new ConcurrentHashMap<>();

    public AuthThrottleFilter(int maxPerMinute) {
        this.maxPerMinute = maxPerMinute;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (maxPerMinute > 0) {
            long minute = Instant.now().getEpochSecond() / 60;
            if (windows.size() > MAX_TRACKED_IPS) {
                windows.clear();
            }
            long[] window = windows.compute(request.getRemoteAddr(), (ip, current) -> {
                if (current == null || current[0] != minute) {
                    return new long[] {minute, 1};
                }
                current[1]++;
                return current;
            });
            if (window[1] > maxPerMinute) {
                ((HttpServletResponse) response).sendError(TOO_MANY_REQUESTS, "too many attempts; slow down");
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
