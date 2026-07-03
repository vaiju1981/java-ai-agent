package dev.vaijanath.aiagent.demos.analytics;

import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vaijanath.aiagent.agent.Agent;
import dev.vaijanath.aiagent.agent.AgentRequest;
import dev.vaijanath.aiagent.agent.RequestContext;
import dev.vaijanath.aiagent.model.Message;
import dev.vaijanath.aiagent.model.ModelPort;
import dev.vaijanath.aiagent.model.ModelRequest;
import dev.vaijanath.aiagent.model.ModelResponse;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SessionMemoryTest {

    @TempDir
    Path tempDir;

    @Test
    void agentRemembersPriorTurnsInSameSession() throws Exception {
        Map<String, Path> csvs = new LinkedHashMap<>();
        csvs.put("customers", Fixtures.customers(tempDir));
        csvs.put("orders", Fixtures.orders(tempDir));
        try (CsvSource src = CsvSource.of(tempDir.resolve("mem.duckdb"), csvs)) {
            Recording recording = new Recording();
            Agent agent = AnalyticsAgent.create(src, recording);
            RequestContext session = RequestContext.session("s1");

            agent.run(new AgentRequest("first question about revenue", session));
            agent.run(new AgentRequest("second question", session));

            assertTrue(recording.lastText().contains("first question about revenue"),
                    "the second turn should carry the first turn from session memory");
        }
    }

    /** Test-only ModelPort (unit mock, not a shipped brain): records the last request, replies plainly. */
    private static final class Recording implements ModelPort {
        private ModelRequest last;

        @Override
        public ModelResponse chat(ModelRequest request) {
            this.last = request;
            return ModelResponse.text("noted");
        }

        String lastText() {
            StringBuilder sb = new StringBuilder();
            for (Message m : last.messages()) {
                sb.append(m.role()).append(':').append(m.content()).append('\n');
            }
            return sb.toString();
        }
    }
}
