package dev.vaijanath.aiagent.demos.analytics;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import dev.vaijanath.aiagent.agent.Agent;
import dev.vaijanath.aiagent.agent.AgentRequest;
import dev.vaijanath.aiagent.agent.AgentResponse;
import dev.vaijanath.aiagent.langchain4j.OllamaModelPorts;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@Tag("manual")
class AnalyticsAgentLiveTest {

    @TempDir
    Path tempDir;

    @Test
    void answersNaturalLanguageQuestionViaTools() throws Exception {
        String base = System.getenv().getOrDefault("OLLAMA_BASE_URL", "http://localhost:11434");
        String modelName = System.getenv().getOrDefault("ANALYTICS_MODEL", "gemma4:31b-cloud");
        assumeTrue(reachable(base), "Ollama not reachable at " + base);

        Map<String, Path> csvs = new LinkedHashMap<>();
        csvs.put("customers", Fixtures.customers(tempDir));
        csvs.put("orders", Fixtures.orders(tempDir));
        try (CsvSource src = CsvSource.of(tempDir.resolve("agent.duckdb"), csvs)) {
            dev.vaijanath.aiagent.observe.AgentObserver trace = new dev.vaijanath.aiagent.observe.AgentObserver() {
                @Override
                public void onToolCall(dev.vaijanath.aiagent.model.ToolCall call) {
                    System.out.println("TOOLCALL " + call.name() + " args=" + call.argumentsJson());
                }

                @Override
                public void onToolResult(String toolName, dev.vaijanath.aiagent.tool.ToolResult result) {
                    String c = result.content();
                    System.out.println("TOOLRESULT " + toolName + " err=" + result.error()
                            + " " + (c.length() > 300 ? c.substring(0, 300) : c));
                }
            };
            Agent agent = AnalyticsAgent.create(src, OllamaModelPorts.ollama(base, modelName), trace);
            AgentResponse resp = agent.run(new AgentRequest("What is total revenue by customer segment?"));
            System.out.println("AGENT ANSWER: " + resp.output());

            assertFalse(resp.blocked(), "agent should not be blocked");
            assertTrue(resp.output().toLowerCase(Locale.ROOT).contains("enterprise"),
                    "answer should reference the enterprise segment, was: " + resp.output());
        }
    }

    private static boolean reachable(String base) {
        try {
            HttpURLConnection c = (HttpURLConnection) URI.create(base + "/api/tags").toURL().openConnection();
            c.setConnectTimeout(1500);
            c.setReadTimeout(1500);
            c.connect();
            boolean ok = c.getResponseCode() == 200;
            c.disconnect();
            return ok;
        } catch (Exception e) {
            return false;
        }
    }
}
