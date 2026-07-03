package dev.vaijanath.aiagent.demos.analytics;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import dev.vaijanath.aiagent.agent.Agent;
import dev.vaijanath.aiagent.agent.AgentRequest;
import dev.vaijanath.aiagent.agent.AgentResponse;
import dev.vaijanath.aiagent.agent.RequestContext;
import dev.vaijanath.aiagent.langchain4j.OllamaModelPorts;
import dev.vaijanath.aiagent.model.ToolCall;
import dev.vaijanath.aiagent.observe.AgentObserver;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Live: a follow-up question resolves from session memory (multi-turn), sharing one session id. */
@Tag("manual")
class MultiTurnLiveTest {

    @TempDir
    Path tempDir;

    @Test
    void followUpResolvesFromSessionMemory() throws Exception {
        String base = System.getenv().getOrDefault("OLLAMA_BASE_URL", "http://localhost:11434");
        String modelName = System.getenv().getOrDefault("ANALYTICS_MODEL", "gemma4:31b-cloud");
        assumeTrue(reachable(base), "Ollama not reachable at " + base);

        Map<String, Path> csvs = new LinkedHashMap<>();
        csvs.put("customers", Fixtures.customers(tempDir));
        csvs.put("orders", Fixtures.orders(tempDir));

        List<String> calls = new ArrayList<>();
        AgentObserver trace = new AgentObserver() {
            @Override
            public void onToolCall(ToolCall call) {
                calls.add(call.name());
            }
        };

        try (CsvSource src = CsvSource.of(tempDir.resolve("chat.duckdb"), csvs)) {
            Agent agent = AnalyticsAgent.create(src, OllamaModelPorts.ollama(base, modelName), trace);
            RequestContext session = RequestContext.session("chat1");

            AgentResponse t1 = agent.run(new AgentRequest("What is total revenue by customer segment?", session));
            List<String> turn1Calls = new ArrayList<>(calls);
            AgentResponse t2 = agent.run(new AgentRequest("Which of those segments was higher?", session));

            System.out.println("TURN1 calls=" + turn1Calls + " -> " + t1.output());
            System.out.println("TURN2 -> " + t2.output());

            assertFalse(t2.blocked(), "follow-up should not be blocked");
            assertTrue(t2.output().toLowerCase(Locale.ROOT).contains("enterprise"),
                    "follow-up must use turn-1 context (enterprise 950.5 > smb 45), was: " + t2.output());
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
