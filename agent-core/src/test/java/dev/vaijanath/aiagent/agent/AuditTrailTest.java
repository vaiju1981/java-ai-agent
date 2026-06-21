package dev.vaijanath.aiagent.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vaijanath.aiagent.audit.AuditEvent;
import dev.vaijanath.aiagent.audit.FileAuditSink;
import dev.vaijanath.aiagent.audit.InMemoryAuditSink;
import dev.vaijanath.aiagent.model.ModelPort;
import dev.vaijanath.aiagent.model.ModelRequest;
import dev.vaijanath.aiagent.model.ModelResponse;
import dev.vaijanath.aiagent.model.ToolCall;
import dev.vaijanath.aiagent.tool.Tool;
import dev.vaijanath.aiagent.tool.ToolApprovers;
import dev.vaijanath.aiagent.tool.ToolEffect;
import dev.vaijanath.aiagent.tool.ToolResult;
import dev.vaijanath.aiagent.tool.ToolSpec;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AuditTrailTest {

    @Test
    void recordsTurnAndDeniedToolWithIdentity() {
        ModelPort callsThenAnswers = new ModelPort() {
            private int n = 0;

            @Override
            public ModelResponse chat(ModelRequest request) {
                return (++n == 1)
                        ? new ModelResponse("", List.of(new ToolCall("c1", "wipe", "{}")))
                        : ModelResponse.text("done");
            }
        };
        Tool wipe = new Tool() {
            @Override
            public ToolSpec spec() {
                return new ToolSpec("wipe", "delete things",
                        "{\"type\":\"object\",\"properties\":{}}", ToolEffect.EFFECTFUL);
            }

            @Override
            public ToolResult invoke(String argumentsJson) {
                return ToolResult.ok("done");
            }
        };
        InMemoryAuditSink audit = new InMemoryAuditSink();
        RequestContext ctx = new RequestContext("sess-1", "alice", "acme", "trace-9", null, null);

        DefaultAgent.builder()
                .model(callsThenAnswers)
                .tool(wipe)
                .toolApprover(ToolApprovers.denyEffectful())
                .auditSink(audit)
                .build()
                .run(new AgentRequest("wipe everything", ctx));

        List<AuditEvent> events = audit.events();
        List<String> types = events.stream().map(AuditEvent::type).toList();
        assertTrue(types.contains("turn.start"), types.toString());
        assertTrue(types.contains("tool.denied"), types.toString());
        assertTrue(types.contains("turn.end"), types.toString());
        assertTrue(events.stream().allMatch(e -> e.sessionId().equals("sess-1")
                && e.principal().equals("alice") && e.tenant().equals("acme")
                && e.traceId().equals("trace-9")), "identity must travel on every event");
        assertTrue(events.stream().allMatch(e -> e.eventId() != null && e.at() != null));
    }

    @Test
    void fileSinkPersistsDurably(@TempDir Path dir) throws Exception {
        Path log = dir.resolve("audit.log");
        try (FileAuditSink sink = new FileAuditSink(log)) {
            sink.record(AuditEvent.now("tool.denied", "t", "s", "p", "acme", "tool=wipe reason=effectful"));
            sink.record(AuditEvent.now("turn.end", "t", "s", "p", "acme", "completed"));
        }
        List<String> lines = Files.readAllLines(log);
        assertEquals(2, lines.size());
        assertTrue(lines.get(0).contains("tool.denied"));
        assertTrue(lines.get(0).contains("\t"), "tab-separated line");
    }
}
