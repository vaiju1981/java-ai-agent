package dev.vaijanath.aiagent.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vaijanath.aiagent.model.ToolCall;
import dev.vaijanath.aiagent.tool.ContextualTool;
import dev.vaijanath.aiagent.tool.ToolCallContext;
import dev.vaijanath.aiagent.tool.ToolEffect;
import dev.vaijanath.aiagent.tool.ToolInvocation;
import dev.vaijanath.aiagent.tool.ToolResult;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class AgentsTest {

    private static ToolInvocation invocation(ContextualTool tool, String argumentsJson) {
        ToolCallContext ctx = new ToolCallContext(
                tool.spec(), argumentsJson, "alice", "acme", "trace-1", "session-1", null);
        return new ToolInvocation(new ToolCall("call-1", tool.name(), argumentsJson), ctx);
    }

    @Test
    void runsTheWrappedAgentWithTheExtractedRequest() {
        Agent specialist = req -> AgentResponse.completed("did: " + req.input());
        ContextualTool tool = Agents.asTool("researcher", "gathers facts", specialist);

        ToolResult result = tool.invoke(invocation(tool, "{\"request\":\"find the capital of France\"}"));

        assertFalse(result.error());
        assertEquals("did: find the capital of France", result.content());
    }

    @Test
    void carriesCallerIdentityIntoAFreshChildSession() {
        AtomicReference<RequestContext> seen = new AtomicReference<>();
        Agent specialist = req -> {
            seen.set(req.context());
            return AgentResponse.completed("ok");
        };
        ContextualTool tool = Agents.asTool("s", "spec", specialist);

        tool.invoke(invocation(tool, "{\"request\":\"x\"}"));

        RequestContext ctx = seen.get();
        assertEquals("alice", ctx.principal());
        assertEquals("acme", ctx.tenant());
        assertEquals("trace-1", ctx.traceId()); // trace carried for correlation
        assertFalse(ctx.sessionId().equals("session-1")); // but a fresh child session
    }

    @Test
    void blockedSpecialistBecomesAToolError() {
        Agent specialist = req -> AgentResponse.blocked("not allowed", "guardrail");
        ContextualTool tool = Agents.asTool("s", "spec", specialist);

        ToolResult result = tool.invoke(invocation(tool, "{\"request\":\"x\"}"));

        assertTrue(result.error());
        assertEquals("not allowed", result.content());
    }

    @Test
    void fallsBackToRawArgumentsWhenThereIsNoRequestField() {
        Agent specialist = req -> AgentResponse.completed("echo:" + req.input());
        ContextualTool tool = Agents.asTool("s", "spec", specialist);

        ToolResult result = tool.invoke(invocation(tool, "plain text, not json"));

        assertEquals("echo:plain text, not json", result.content());
    }

    @Test
    void specReflectsNameDescriptionAndEffect() {
        ContextualTool tool = Agents.asTool(
                "writer", "drafts prose", req -> AgentResponse.completed(""), ToolEffect.READ_ONLY);

        assertEquals("writer", tool.spec().name());
        assertEquals("drafts prose", tool.spec().description());
        assertEquals(ToolEffect.READ_ONLY, tool.spec().effect());
        assertTrue(tool.spec().parametersJsonSchema().contains("request"));
    }

    @Test
    void defaultEffectIsEffectful() {
        ContextualTool tool = Agents.asTool("s", "spec", req -> AgentResponse.completed(""));
        assertEquals(ToolEffect.EFFECTFUL, tool.spec().effect());
    }

    @Test
    void extractStringHandlesEscapesAndUnicode() {
        // value contains: a newline (\n), escaped quotes (\"), a backslash (\\), and A (= 'A')
        String json = "{\"request\":\"line1\\nq=\\\"x\\\" path=C:\\\\t \\u0041\"}";

        assertEquals("line1\nq=\"x\" path=C:\\t A", Agents.extractString(json, "request"));
    }

    @Test
    void extractStringReturnsNullForMissingOrNonStringField() {
        assertNull(Agents.extractString("{\"other\":\"v\"}", "request"));
        assertNull(Agents.extractString("{\"request\":42}", "request"));
        assertNull(Agents.extractString(null, "request"));
    }
}
