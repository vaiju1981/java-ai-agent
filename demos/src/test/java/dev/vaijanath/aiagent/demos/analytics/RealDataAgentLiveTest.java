package dev.vaijanath.aiagent.demos.analytics;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import dev.vaijanath.aiagent.agent.Agent;
import dev.vaijanath.aiagent.agent.AgentRequest;
import dev.vaijanath.aiagent.agent.AgentResponse;
import dev.vaijanath.aiagent.langchain4j.OllamaModelPorts;
import dev.vaijanath.aiagent.model.ToolCall;
import dev.vaijanath.aiagent.observe.AgentObserver;
import dev.vaijanath.aiagent.tool.ToolResult;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Capstone: the agent answers a time-scoped NL question over the real multi-GB REDROCK data. */
@Tag("manual")
class RealDataAgentLiveTest {

    @TempDir
    Path tempDir;

    @Test
    void agentAppliesTimeWindowOverRealRedrockData() throws Exception {
        String dir = System.getenv("ANALYTICS_REAL_DATA_DIR");
        assumeTrue(dir != null && Files.isDirectory(Path.of(dir)), "set ANALYTICS_REAL_DATA_DIR");
        String base = System.getenv().getOrDefault("OLLAMA_BASE_URL", "http://localhost:11434");
        String modelName = System.getenv().getOrDefault("ANALYTICS_MODEL", "gemma4:31b-cloud");
        assumeTrue(reachable(base), "Ollama not reachable at " + base);

        Map<String, Path> csvs = new LinkedHashMap<>();
        csvs.put("playerVisits", Path.of(dir, "STATION_playerVisits_REDROCK.csv"));

        List<String> toolCalls = new ArrayList<>();
        AgentObserver trace = new AgentObserver() {
            @Override
            public void onToolCall(ToolCall call) {
                toolCalls.add(call.name() + " " + call.argumentsJson());
                System.out.println("TOOLCALL " + call.name() + " args=" + call.argumentsJson());
            }

            @Override
            public void onToolResult(String toolName, ToolResult result) {
                String c = result.content();
                System.out.println("TOOLRESULT " + toolName + " err=" + result.error()
                        + " " + (c.length() > 260 ? c.substring(0, 260) : c));
            }
        };

        try (CsvSource src = CsvSource.of(tempDir.resolve("real-agent.duckdb"), csvs)) {
            Agent agent = AnalyticsAgent.create(src, OllamaModelPorts.ollama(base, modelName), trace);
            AgentResponse resp = agent.run(new AgentRequest(
                    "What is the total coinIn by clubLevel in the last 30 days? List each club level."));
            System.out.println("AGENT ANSWER: " + resp.output());

            assertFalse(resp.blocked(), "agent should not be blocked");
            boolean appliedWindow = toolCalls.stream().anyMatch(c -> c.startsWith("run_query")
                    && (c.contains("lastDays") || c.toLowerCase(Locale.ROOT).contains("day")));
            assertTrue(appliedWindow, "agent must apply a time window for 'last 30 days', calls were: " + toolCalls);
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
