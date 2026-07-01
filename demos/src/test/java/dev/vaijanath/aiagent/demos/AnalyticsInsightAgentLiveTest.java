package dev.vaijanath.aiagent.demos;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

class AnalyticsInsightAgentLiveTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String MODEL = "gemma4:31b-cloud";

    @Test
    void ollamaGemmaLiveModelAnswersWhenExplicitlyConfigured() throws Exception {
        String baseUrl = System.getenv("OLLAMA_BASE_URL");
        Assumptions.assumeTrue(baseUrl != null && !baseUrl.isBlank(), "OLLAMA_BASE_URL is not set");

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        String body = MAPPER.writeValueAsString(Map.of(
                "model", MODEL,
                "stream", false,
                "messages", java.util.List.of(
                        Map.of("role", "system", "content", "Reply with exactly ANALYTICS_LIVE_OK and no other text."),
                        Map.of("role", "user", "content", "Run the analytics demo live-model smoke check."))));
        HttpRequest request = HttpRequest.newBuilder(URI.create(stripTrailingSlash(baseUrl) + "/api/chat"))
                .timeout(Duration.ofMinutes(2))
                .header("content-type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertTrue(response.statusCode() >= 200 && response.statusCode() < 300, response.body());
        JsonNode json = MAPPER.readTree(response.body());
        String content = json.path("message").path("content").asText();
        assertTrue(content.toUpperCase(Locale.ROOT).contains("ANALYTICS_LIVE_OK"), content);
    }

    private static String stripTrailingSlash(String value) {
        String stripped = value.strip();
        while (stripped.endsWith("/")) {
            stripped = stripped.substring(0, stripped.length() - 1);
        }
        return stripped;
    }
}
