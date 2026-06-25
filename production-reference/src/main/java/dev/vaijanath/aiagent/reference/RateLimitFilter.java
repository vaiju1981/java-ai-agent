package dev.vaijanath.aiagent.reference;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Edge admission control in front of the (paid, inference-backed) agent endpoint: it rejects bodies
 * larger than a cap and throttles callers to a per-minute token bucket. A body-bearing request must
 * declare its {@code Content-Length} (else {@code 411}), so the size cap cannot be bypassed with chunked
 * encoding. The bucket is keyed by API key, then by the connection's remote address — never by a
 * client-supplied header an attacker could rotate to dodge the limit.
 *
 * <p>The bucket store is in-memory and bounded by an LRU cap (so it can't grow without limit); limits
 * are per service instance, so a multi-replica deployment needs a shared store (e.g. Redis) for a global
 * limit. Adequate as a baseline and for single-node.
 */
class RateLimitFilter extends OncePerRequestFilter {

    private static final int MAX_BUCKETS = 100_000;
    private static final Set<String> BODY_METHODS = Set.of("POST", "PUT", "PATCH");

    private final int permitsPerMinute; // 0 = throttling disabled
    private final long maxRequestBytes;
    private final Map<String, Bucket> buckets;

    RateLimitFilter(int permitsPerMinute, long maxRequestBytes) {
        this.permitsPerMinute = Math.max(0, permitsPerMinute);
        this.maxRequestBytes = maxRequestBytes;
        this.buckets = boundedBuckets();
    }

    @SuppressWarnings("serial") // a bounded LRU map; never serialized
    private static Map<String, Bucket> boundedBuckets() {
        return Collections.synchronizedMap(new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Bucket> eldest) {
                return size() > MAX_BUCKETS;
            }
        });
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long declared = request.getContentLengthLong();
        if (declared > maxRequestBytes) {
            reject(response, HttpStatus.PAYLOAD_TOO_LARGE, "request body exceeds " + maxRequestBytes + " bytes");
            return;
        }
        // Unknown length (e.g. chunked) on a body method would let a large body slip past the cap above.
        if (declared < 0 && BODY_METHODS.contains(request.getMethod())) {
            reject(response, HttpStatus.LENGTH_REQUIRED, "a Content-Length header is required");
            return;
        }
        if (permitsPerMinute > 0) {
            Bucket bucket = buckets.computeIfAbsent(clientKey(request), k -> new Bucket(permitsPerMinute));
            if (!bucket.tryAcquire()) {
                response.setHeader(HttpHeaders.RETRY_AFTER, "1");
                reject(response, HttpStatus.TOO_MANY_REQUESTS, "rate limit exceeded; retry shortly");
                return;
            }
        }
        chain.doFilter(request, response);
    }

    private static void reject(HttpServletResponse response, HttpStatus status, String message)
            throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"error\":\"" + message + "\"}");
    }

    private static String clientKey(HttpServletRequest request) {
        String apiKey = request.getHeader(ApiKeyAuthenticationFilter.HEADER);
        if (apiKey != null && !apiKey.isBlank()) {
            return "key:" + apiKey;
        }
        // Auth disabled (dev): key by the connection, not a client-controlled header.
        return "addr:" + request.getRemoteAddr();
    }

    /** A simple token bucket: {@code permitsPerMinute} capacity, refilled continuously. */
    private static final class Bucket {

        private final double capacity;
        private final double refillPerNano;
        private double tokens;
        private long lastRefillNanos;

        Bucket(int permitsPerMinute) {
            this.capacity = permitsPerMinute;
            this.refillPerNano = permitsPerMinute / 60_000_000_000.0;
            this.tokens = permitsPerMinute;
            this.lastRefillNanos = System.nanoTime();
        }

        synchronized boolean tryAcquire() {
            long now = System.nanoTime();
            tokens = Math.min(capacity, tokens + (now - lastRefillNanos) * refillPerNano);
            lastRefillNanos = now;
            if (tokens >= 1.0) {
                tokens -= 1.0;
                return true;
            }
            return false;
        }
    }
}
