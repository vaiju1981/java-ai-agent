package dev.vaijanath.aiagent.fincopilot;

import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vaijanath.aiagent.agent.Agent;
import dev.vaijanath.aiagent.agent.AgentRequest;
import dev.vaijanath.aiagent.agent.AgentResponse;
import dev.vaijanath.aiagent.agent.ProductionAgentRuntime;
import dev.vaijanath.aiagent.audit.InMemoryAuditSink;
import dev.vaijanath.aiagent.langchain4j.OllamaModelPorts;
import dev.vaijanath.aiagent.memory.InMemoryConversationStore;
import dev.vaijanath.aiagent.model.ModelPort;
import dev.vaijanath.aiagent.tool.Tool;
import dev.vaijanath.aiagent.tool.ToolEffect;
import dev.vaijanath.aiagent.tool.ToolResult;
import dev.vaijanath.aiagent.tool.ToolSpec;
import dev.vaijanath.aiagent.tools.jsonschema.JsonSchemaToolValidator;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * A live probe that confirms the configured Ollama model (gemma4:31b-cloud by default) actually performs
 * tool-calling through the governed runtime — the precondition for the M1 Analyst, and the v0.2.0 plan's
 * biggest model risk. Gated on {@code OLLAMA_TOOLCALL_PROBE} so it is skipped in CI and ordinary builds.
 */
class ToolCallingProbeTest {

    @Test
    @EnabledIfEnvironmentVariable(named = "OLLAMA_TOOLCALL_PROBE", matches = ".+")
    void modelInvokesAToolAndUsesItsResult() {
        String baseUrl = System.getenv().getOrDefault("OLLAMA_BASE_URL", "http://localhost:11434");
        String modelName = System.getenv().getOrDefault("OLLAMA_MODEL", "gemma4:31b-cloud");
        ModelPort model = OllamaModelPorts.ollama(baseUrl, modelName);

        AtomicInteger calls = new AtomicInteger();
        Tool balance = new Tool() {
            @Override
            public ToolSpec spec() {
                return new ToolSpec(
                        "get_account_balance",
                        "Returns the current balance for an account id.",
                        "{\"type\":\"object\",\"properties\":{\"account\":{\"type\":\"string\"}},"
                                + "\"required\":[\"account\"]}",
                        ToolEffect.READ_ONLY);
            }

            @Override
            public ToolResult invoke(String argumentsJson) {
                calls.incrementAndGet();
                return ToolResult.ok("{\"account\":\"chk-001\",\"balance\":4217.55,\"currency\":\"USD\"}");
            }
        };

        Agent agent = ProductionAgentRuntime.builder()
                .model(model)
                .conversationStore(new InMemoryConversationStore())
                .auditSink(new InMemoryAuditSink())
                .argumentValidator(new JsonSchemaToolValidator())
                .tool(balance)
                .modelTimeout(Duration.ofSeconds(120))
                .toolTimeout(Duration.ofSeconds(20))
                .maxSteps(6)
                .systemPrompt("You are a finance assistant. To answer about an account balance you MUST call the "
                        + "get_account_balance tool; never guess. After the tool returns, state the balance.")
                .build();

        AgentResponse response =
                agent.run(new AgentRequest("What is the current balance of account chk-001? Use the tool."));

        System.out.println("[probe] toolCalls=" + calls.get() + " | answer=" + response.output());
        assertTrue(calls.get() >= 1, "the model should have invoked get_account_balance");
        assertTrue(
                response.output().contains("4217") || response.output().contains("4,217"),
                "the answer should reflect the tool result: " + response.output());
    }
}
