package dev.vaijanath.aiagent.springboot.health;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;

/**
 * Readiness that reflects the reachability of an HTTP model backend (e.g. Ollama): an agent whose model
 * is down would fail every turn, so the instance should not report ready and take traffic. Register it
 * in the {@code readiness} health group — the bean name becomes the contributor key — so a backend
 * outage drains the instance from a load balancer instead of serving failing requests.
 *
 * <p>It issues a short GET to the base URL and treats any response below {@code 500} as up (the backend
 * is answering, even if the root path itself isn't a real route). Connect/read timeouts and errors are
 * down. The probe is deliberately cheap so it can run on the readiness interval without load.
 */
public final class ModelEndpointHealthIndicator implements HealthIndicator {

    private final URI baseUri;
    private final Duration timeout;
    private final HttpClient http;

    public ModelEndpointHealthIndicator(String baseUrl) {
        this(baseUrl, Duration.ofSeconds(2));
    }

    public ModelEndpointHealthIndicator(String baseUrl, Duration timeout) {
        this.baseUri = URI.create(baseUrl);
        this.timeout = timeout;
        this.http = HttpClient.newBuilder().connectTimeout(timeout).build();
    }

    @Override
    public Health health() {
        HttpRequest request = HttpRequest.newBuilder(baseUri).timeout(timeout).GET().build();
        try {
            HttpResponse<Void> response = http.send(request, HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() < 500) {
                return Health.up().withDetail("baseUrl", baseUri.toString()).build();
            }
            return Health.down()
                    .withDetail("baseUrl", baseUri.toString())
                    .withDetail("status", response.statusCode())
                    .build();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Health.down().withDetail("baseUrl", baseUri.toString()).withDetail("error", "interrupted").build();
        } catch (IOException e) {
            return Health.down()
                    .withDetail("baseUrl", baseUri.toString())
                    .withDetail("error", e.getClass().getSimpleName())
                    .build();
        }
    }
}
