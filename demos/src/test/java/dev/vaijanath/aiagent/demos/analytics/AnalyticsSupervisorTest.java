package dev.vaijanath.aiagent.demos.analytics;

import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vaijanath.aiagent.agent.Agent;
import dev.vaijanath.aiagent.agent.AgentRequest;
import dev.vaijanath.aiagent.model.Message;
import dev.vaijanath.aiagent.model.ModelPort;
import dev.vaijanath.aiagent.model.ModelRequest;
import dev.vaijanath.aiagent.model.ModelResponse;
import dev.vaijanath.aiagent.model.Role;
import dev.vaijanath.aiagent.supervise.Router;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AnalyticsSupervisorTest {

    @TempDir
    Path tempDir;

    @Test
    void routesToTheRightSpecialist() throws Exception {
        Map<String, Path> csvs = new LinkedHashMap<>();
        csvs.put("customers", Fixtures.customers(tempDir));
        csvs.put("orders", Fixtures.orders(tempDir));
        try (CsvSource src = CsvSource.of(tempDir.resolve("sup.duckdb"), csvs)) {
            SemanticModel model = SemanticModel.fromProfile(DatasetProfiler.profile(src));
            // Deterministic router (no LLM): route structure/column questions to the schema guide.
            Router router = (input, agents) -> {
                String q = input.toLowerCase(Locale.ROOT);
                return q.contains("column") || q.contains("structure") ? "schema_guide" : "analyst";
            };
            Agent supervisor = AnalyticsSupervisor.create(src, model, new StubModel(), router);

            String schema = supervisor.run(new AgentRequest("what columns exist?")).output();
            String data = supervisor.run(new AgentRequest("total revenue by segment")).output();

            assertTrue(schema.contains("SCHEMA-GUIDE"), "structure question routed to schema_guide, was: " + schema);
            assertTrue(data.contains("ANALYST"), "data question routed to analyst, was: " + data);
        }
    }

    /** Stub ModelPort: replies based on which specialist's system prompt it sees (no tool calls). */
    private static final class StubModel implements ModelPort {
        @Override
        public ModelResponse chat(ModelRequest request) {
            String system = request.messages().stream()
                    .filter(m -> m.role() == Role.SYSTEM)
                    .map(Message::content).findFirst().orElse("");
            return ModelResponse.text(system.contains("structure") ? "SCHEMA-GUIDE ANSWER" : "ANALYST ANSWER");
        }
    }
}
