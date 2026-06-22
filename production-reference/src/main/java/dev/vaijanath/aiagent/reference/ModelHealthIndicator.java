package dev.vaijanath.aiagent.reference;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;

/**
 * Readiness reflects model reachability: an agent whose model backend is down would 503 every turn,
 * so it should not report ready and take traffic. Contributes to the {@code readiness} group, so a
 * model outage drains the instance from a load balancer instead of serving failing requests.
 */
class ModelHealthIndicator implements HealthIndicator {

    private final URI baseUri;
    private final HttpClient http;

    ModelHealthIndicator(String baseUrl) {
        this.baseUri = URI.create(baseUrl);
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    }

    @Override
    public Health health() {
        HttpRequest request = HttpRequest.newBuilder(baseUri).timeout(Duration.ofSeconds(2)).GET().build();
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
        } catch (Exception e) {
            return Health.down()
                    .withDetail("baseUrl", baseUri.toString())
                    .withDetail("error", e.getClass().getSimpleName())
                    .build();
        }
    }
}
