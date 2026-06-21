package dev.vaijanath.aiagent.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.vaijanath.aiagent.audit.InMemoryAuditSink;
import dev.vaijanath.aiagent.guardrail.GuardrailDecision;
import dev.vaijanath.aiagent.guardrail.GuardrailStage;
import dev.vaijanath.aiagent.memory.InMemoryConversationStore;
import dev.vaijanath.aiagent.model.Message;
import dev.vaijanath.aiagent.model.ModelPort;
import dev.vaijanath.aiagent.model.ModelResponse;
import dev.vaijanath.aiagent.tool.Tool;
import dev.vaijanath.aiagent.tool.ToolArgumentValidator;
import dev.vaijanath.aiagent.tool.ToolResult;
import dev.vaijanath.aiagent.tool.ToolSpec;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ProductionAgentRuntimeTest {

    @Test
    void requiresTheProductionSafetyDependencies() {
        ModelPort model = request -> ModelResponse.text("ok");

        assertThrows(NullPointerException.class, () -> ProductionAgentRuntime.builder()
                .model(model)
                .build());
    }

    @Test
    void failsAssemblyWhenAToolSchemaIsInvalid() {
        Tool invalid = new Tool() {
            @Override
            public ToolSpec spec() {
                return new ToolSpec("broken", "", "not-json");
            }

            @Override
            public ToolResult invoke(String argumentsJson) {
                return ToolResult.ok("should not run");
            }
        };
        ToolArgumentValidator validator = new ToolArgumentValidator() {
            @Override
            public Optional<String> validate(ToolSpec spec, String argumentsJson) {
                return Optional.empty();
            }

            @Override
            public Optional<String> validateSchema(ToolSpec spec) {
                return Optional.of("invalid JSON schema");
            }
        };

        assertThrows(IllegalArgumentException.class, () -> ProductionAgentRuntime.builder()
                .model(request -> ModelResponse.text("ok"))
                .conversationStore(new InMemoryConversationStore())
                .auditSink(new InMemoryAuditSink())
                .argumentValidator(validator)
                .tool(invalid)
                .build());
    }

    @Test
    void emitsOneGovernedLifecycleForASuccessfulTurn() {
        InMemoryAuditSink audit = new InMemoryAuditSink();
        Agent agent = ProductionAgentRuntime.builder()
                .model(request -> ModelResponse.text("ok"))
                .conversationStore(new InMemoryConversationStore())
                .auditSink(audit)
                .argumentValidator(ToolArgumentValidator.none())
                .build();

        AgentResponse response = agent.run(new AgentRequest("hello"));

        assertEquals("ok", response.output());
        assertEquals(1, audit.events().stream().filter(e -> e.type().equals("turn.start")).count());
        assertEquals(1, audit.events().stream().filter(e -> e.type().equals("turn.end")).count());
    }

    @Test
    void persistsOnlyTheGuardedOutput() {
        InMemoryConversationStore conversations = new InMemoryConversationStore();
        Agent agent = ProductionAgentRuntime.builder()
                .model(request -> ModelResponse.text("raw-secret"))
                .conversationStore(conversations)
                .auditSink(new InMemoryAuditSink())
                .argumentValidator(ToolArgumentValidator.none())
                .guardrail((stage, content) -> stage == GuardrailStage.OUTPUT
                        ? GuardrailDecision.allow("safe-output")
                        : GuardrailDecision.allow(content))
                .build();
        RequestContext context = RequestContext.session("session");

        AgentResponse response = agent.run(new AgentRequest("hello", context));
        List<Message> history = conversations.withMemory(
                context.tenant(), context.sessionId(), memory -> memory.history());

        assertEquals("safe-output", response.output());
        assertEquals("safe-output", history.get(history.size() - 1).content());
    }
}
