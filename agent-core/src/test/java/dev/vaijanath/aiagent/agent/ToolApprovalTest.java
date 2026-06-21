package dev.vaijanath.aiagent.agent;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vaijanath.aiagent.model.Message;
import dev.vaijanath.aiagent.model.ModelPort;
import dev.vaijanath.aiagent.model.ModelRequest;
import dev.vaijanath.aiagent.model.ModelResponse;
import dev.vaijanath.aiagent.model.Role;
import dev.vaijanath.aiagent.model.ToolCall;
import dev.vaijanath.aiagent.tool.Tool;
import dev.vaijanath.aiagent.tool.ToolApprovers;
import dev.vaijanath.aiagent.tool.ToolEffect;
import dev.vaijanath.aiagent.tool.ToolResult;
import dev.vaijanath.aiagent.tool.ToolSpec;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class ToolApprovalTest {

    /** A model that calls {@code toolName} once, then echoes the tool result. */
    private static ModelPort callsThenEchoes(String toolName) {
        return new ModelPort() {
            private int calls = 0;

            @Override
            public ModelResponse chat(ModelRequest request) {
                calls++;
                if (calls == 1) {
                    return new ModelResponse("", List.of(new ToolCall("c1", toolName, "{}")));
                }
                String tool = request.messages().stream()
                        .filter(m -> m.role() == Role.TOOL)
                        .reduce((a, b) -> b)
                        .map(Message::content)
                        .orElse("");
                return ModelResponse.text("observed: " + tool);
            }
        };
    }

    private static Tool namedTool(String name, AtomicBoolean invokedFlag, String result) {
        return namedTool(name, invokedFlag, result, ToolEffect.EFFECTFUL);
    }

    private static Tool namedTool(String name, AtomicBoolean invokedFlag, String result, ToolEffect effect) {
        return new Tool() {
            @Override
            public ToolSpec spec() {
                return new ToolSpec(name, name, "{\"type\":\"object\",\"properties\":{}}", effect);
            }

            @Override
            public ToolResult invoke(String argumentsJson) {
                invokedFlag.set(true);
                return ToolResult.ok(result);
            }
        };
    }

    @Test
    void deniedToolIsNotExecuted() {
        AtomicBoolean invoked = new AtomicBoolean(false);
        AgentResponse r = DefaultAgent.builder()
                .model(callsThenEchoes("danger"))
                .tool(namedTool("danger", invoked, "did damage"))
                .toolApprover(ToolApprovers.allowList("safe")) // 'danger' not allowed
                .build()
                .run(new AgentRequest("go"));

        assertFalse(invoked.get(), "a denied tool must not run");
        assertTrue(r.output().contains("not permitted"), "got: " + r.output());
    }

    @Test
    void allowedToolRuns() {
        AtomicBoolean invoked = new AtomicBoolean(false);
        AgentResponse r = DefaultAgent.builder()
                .model(callsThenEchoes("safe"))
                .tool(namedTool("safe", invoked, "42"))
                .toolApprover(ToolApprovers.allowList("safe"))
                .build()
                .run(new AgentRequest("go"));

        assertTrue(invoked.get(), "an allowed tool should run");
        assertTrue(r.output().contains("42"), "got: " + r.output());
    }

    @Test
    void effectfulToolIsDeniedByDefaultUnderDenyEffectful() {
        AtomicBoolean invoked = new AtomicBoolean(false);
        AgentResponse r = DefaultAgent.builder()
                .model(callsThenEchoes("delete"))
                .tool(namedTool("delete", invoked, "deleted", ToolEffect.EFFECTFUL))
                .toolApprover(ToolApprovers.denyEffectful())
                .build()
                .run(new AgentRequest("go"));

        assertFalse(invoked.get(), "an effectful tool must be denied by default");
        assertTrue(r.output().contains("not permitted"), "got: " + r.output());
    }

    @Test
    void readOnlyToolRunsUnderDenyEffectful() {
        AtomicBoolean invoked = new AtomicBoolean(false);
        AgentResponse r = DefaultAgent.builder()
                .model(callsThenEchoes("lookup"))
                .tool(namedTool("lookup", invoked, "value", ToolEffect.READ_ONLY))
                .toolApprover(ToolApprovers.denyEffectful())
                .build()
                .run(new AgentRequest("go"));

        assertTrue(invoked.get(), "a read-only tool should run under denyEffectful");
        assertTrue(r.output().contains("value"), "got: " + r.output());
    }

    @Test
    void invalidArgumentsAreRejectedBeforeInvoking() {
        AtomicBoolean invoked = new AtomicBoolean(false);
        AgentResponse r = DefaultAgent.builder()
                .model(callsThenEchoes("lookup"))
                .tool(namedTool("lookup", invoked, "value", ToolEffect.READ_ONLY))
                .toolArgumentValidator((spec, args) -> java.util.Optional.of("missing field"))
                .build()
                .run(new AgentRequest("go"));

        assertFalse(invoked.get(), "invalid arguments must not reach the tool");
        assertTrue(r.output().contains("invalid"), "got: " + r.output());
    }
}
